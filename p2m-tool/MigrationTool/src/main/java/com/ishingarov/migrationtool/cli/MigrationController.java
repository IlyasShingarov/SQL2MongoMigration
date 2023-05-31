package com.ishingarov.migrationtool.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ishingarov.migrationtool.cli.domain.UiColumnEntry;
import com.ishingarov.migrationtool.cli.domain.UiTableEntry;
import com.ishingarov.migrationtool.format.JsonSchemaFormatter;
import com.ishingarov.migrationtool.format.MigrationSchema;
import com.ishingarov.migrationtool.migration.MigrationService;
import com.ishingarov.migrationtool.repository.MetadataRepository;
import com.ishingarov.migrationtool.repository.QueryLogRepository;
import com.ishingarov.migrationtool.repository.domain.*;
import com.ishingarov.migrationtool.storage.MetadataStorage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jline.utils.InfoCmp;
import org.springframework.data.util.Pair;
import org.springframework.shell.component.ConfirmationInput;
import org.springframework.shell.component.MultiItemSelector;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.table.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ishingarov.migrationtool.cli.domain.UiColumnEntry.*;

@ShellComponent
@RequiredArgsConstructor
public class MigrationController extends AbstractShellComponent {


    private final MetadataRepository metadataRepository;
    private final MetadataStorage storage;
    private final JsonSchemaFormatter jsonSchemaFormatter;
    private final QueryLogRepository queryLogRepository;
    private final MigrationService migrationService;

    private final int maxItems = 15;

    @ShellMethod(key = "migration init", value = "Start migration process", group = "Migration")
    public List<String> initMigration() {
        clear(); // Clear screen

        // Getting schema name and saving it
        StringInput.StringInputContext schemaInputContext = getStringInput("Enter schema name", "nbo");
        storage.setSchema(schemaInputContext.getResultValue());

        // Getting list of tables
        storage.setTableNames(metadataRepository.getTableNames(storage.getSchema()));
        List<SelectorItem<String>> tableItems = storage.getTableNames().stream()
                .map(name -> SelectorItem.of(name, name)).toList();

        // Creating multi-select for tables
        List<String> selectedTable = selectMultiItem(tableItems, "Choose tables to make main collections");

        // Save selected table values
        storage.setSelectedTableNames(selectedTable);
        storage.updateJsonSchema();

        return storage.getSelectedTableNames();
    }

    @ShellMethod(key = "migration start", value = "Start migration algorithm", group = "Migration")
    public void startMigration() {
        Map<String, UiTableEntry> uiCurrentTables = storage.getFullJsonSchema().values().stream()
                .map(UiTableEntry::new)
                .filter(uiTable -> storage.getSelectedTableNames().contains(uiTable.getTableName()))
                .collect(Collectors.toMap(UiTableEntry::getTableName, Function.identity()));

        Map<String, Boolean> migrated = storage.getSelectedTableNames().stream()
                .collect(Collectors.toMap(Function.identity(), a -> false));

        Map<String, ObjectNode> results = new HashMap<>();

        while (migrated.values().stream().anyMatch(status -> !status)) {
            clear();

            String currentTable = getCurrentTableName(uiCurrentTables, migrated);
            if (currentTable.equals(ReservedCommands.RETURN.name())) break;

            ObjectNode result = migrateTable(uiCurrentTables.get(currentTable));
            results.put(currentTable, result);

            migrated.replace(currentTable, true);
        }

        storage.setResultJsonSchema(results);
    }


    @ShellMethod(key = "migration finish", value = "Transit data to target DB", group = "Migration")
    public void endMigration() {
        Map<String, MigrationSchema> migrationSchemaMap = storage.getResultJsonSchema().values().stream()
                .map(MigrationSchema::new)
                .collect(Collectors.toMap(MigrationSchema::getName, Function.identity()));

        Set<MigrationSchema> migrationSchemaSet = new HashSet<>(migrationSchemaMap.values());

        Deque<MigrationSchema> queue = new ArrayDeque<>();
        migrationSchemaSet.stream()
                .filter(MigrationSchema::hasNoReferences)
                .forEach(queue::add);

        queue.forEach(migrationSchemaSet::remove);

        while (migrationSchemaSet.size() != 0) {
            Iterator<MigrationSchema> iterator = migrationSchemaSet.iterator();
            while (iterator.hasNext()) {
                MigrationSchema schema = iterator.next();

                var queueNameSet = queue.stream()
                        .map(MigrationSchema::getName)
                        .collect(Collectors.toSet());

                boolean hasQueuedReference = queueNameSet.stream()
                        .anyMatch(schema::hasReference);

                boolean hasUnsafeReference = migrationSchemaSet.stream()
                        .anyMatch(unsafe -> schema.hasReference(unsafe.getName()));

                if (hasQueuedReference && !hasUnsafeReference) {
                    queue.add(schema);
                    iterator.remove(); // Use iterator to safely remove the current schema
                }
                System.out.println("QUEUE: " + queue.stream()
                        .map(MigrationSchema::getName)
                        .toList());
            }
        }

        while (!queue.isEmpty()) {
            var currentSchema = queue.pollFirst();
            System.out.printf("Migrating %s...", currentSchema.getName());
            migrationService.migrateSchema(currentSchema);
        }
    }

    @ShellMethod(key = "dump result", value = "Dump resulting schema to text file", group = "Migration")
    public void dumpResult() {

    }

    @SneakyThrows
    private ObjectNode migrateTable(UiTableEntry currentTableEntry) {
        TableMetaData currentMetadata = currentTableEntry.getMetaData();
        ObjectNode result = jsonSchemaFormatter.metadataToJson(currentTableEntry.getMetaData());

        showTable(currentMetadata);
        printExternalReferencesInfo(currentMetadata);

        // Prepare state for table migration
        Map<String, ColumnStatus> resolvedFk = currentMetadata.getForeignKeyMetadata().stream()
                .collect(Collectors.toMap(ForeignKeyMetadata::pkColumnName, a -> ColumnStatus.UNRESOLVED));

        Map<String, ColumnStatus> resolvedExt = currentMetadata.getExportedRelationships().stream()
                .collect(Collectors.toMap(ExportedRelationshipMetadata::foreignTableName, a -> ColumnStatus.UNRESOLVED));

        // Run while every relationship is resolved
        while (resolvedFk.containsValue(ColumnStatus.UNRESOLVED) || resolvedExt.containsValue(ColumnStatus.UNRESOLVED)) {
            Pair<Pair<String, String>, ColumnKeyStatus> selectionResult = selectRelationshipToResolve(
                    currentMetadata.getForeignKeyMetadata(),
                    currentMetadata.getExportedRelationships(),
                    resolvedFk,
                    resolvedExt
            );

            // Unpack results
            String nextTableName = selectionResult.getFirst().getFirst();
            String columnName = selectionResult.getFirst().getSecond();
            TableMetaData nextMetadata = storage.getFullJsonSchema().get(nextTableName).getSecond();

//            RelationshipType referenceType = defineRelationship(currentMetadata, nextMetadata);

            printJoinInfo(currentMetadata, nextMetadata);

            String currentCommand = selectCommand(nextTableName);
            switch (MigrationCommands.valueOf(currentCommand)) {
                case EMBED -> {
                    RelationshipType referenceType = defineRelationship();
                    var docToEmbed = recursiveStepIn(nextMetadata, currentMetadata);

                    boolean leavePk = confirmation("Do you want to keep primary key?");

                    var cleanJson = jsonSchemaFormatter.jsonTableToEmbed(docToEmbed, leavePk, referenceType);

                    var props = result.get("properties");
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            ((ObjectNode) props).remove(columnName);
                            ((ObjectNode) props).set(columnName, cleanJson);
                        } else {
                            ((ObjectNode) props).set(nextTableName, cleanJson);
                        }
                    }
                }
                case OMIT -> {
                    var props = result.get("properties");
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            ((ObjectNode) props).remove(columnName);
                        } else {
                            ((ObjectNode) props).remove(nextTableName);
                        }
                    }
                }
                case REFERENCE -> {
                    RelationshipType referenceType = defineRelationship();
                    var reference = jsonSchemaFormatter.getReferenceNode(nextMetadata, referenceType);

                    var props = result.get("properties");
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            if (reference instanceof ObjectNode) {
                                ((ObjectNode) reference).put("name", columnName);
                            }
                            if (reference instanceof ArrayNode) {
                                ((ObjectNode) reference.get(0)).put("name", columnName);
                            }
                            ((ObjectNode) props).set(columnName, reference);
                        } else {
                            if (reference instanceof ObjectNode) {
                                ((ObjectNode) reference).put("name", nextTableName);
                            }
                            if (reference instanceof ArrayNode) {
                                ((ObjectNode) reference.get(0)).put("name", nextTableName);
                            }
                            ((ObjectNode) props).set(nextTableName, reference);
                        }
                    }
                }
                default -> { }
            }

            if (selectionResult.getSecond().equals(ColumnKeyStatus.FK)) {
                MigrationCommands command = MigrationCommands.valueOf(currentCommand);
                if (command != MigrationCommands.SKIP) {
                    resolvedFk.replace(columnName, ColumnStatus.fromCommand(command));
                }
            }
            if (selectionResult.getSecond().equals(ColumnKeyStatus.EXT)) {
                MigrationCommands command = MigrationCommands.valueOf(currentCommand);
                if (command != MigrationCommands.SKIP) {
                    resolvedExt.replace(nextTableName, ColumnStatus.fromCommand(command));
                }
            }
        }

        return result;
    }

    private ObjectNode recursiveStepIn(TableMetaData currentTable, TableMetaData previousTable) {
        ObjectNode result = jsonSchemaFormatter.metadataToJson(currentTable);

        var props = result.get("properties");

        if (props instanceof ObjectNode) {
            var prevExt = currentTable.getExportedRelationships().stream()
                    .filter(rel -> rel.foreignTableName().equals(previousTable.getTableName()))
                    .findAny();
            prevExt.ifPresent(exportedRelationshipMetadata -> ((ObjectNode) props).remove(exportedRelationshipMetadata.foreignTableName()));

            var prevFk = currentTable.getForeignKeyMetadata().stream()
                    .filter(fk -> fk.fkTableName().equals(previousTable.getTableName()))
                    .findAny();
            prevFk.ifPresent(foreignKeyMetadata -> ((ObjectNode) props).remove(foreignKeyMetadata.pkColumnName()));
        }

        // Display info
        showTable(currentTable);
        printExternalReferencesInfo(currentTable);

        // Prepare state for table migration
        Map<String, ColumnStatus> resolvedFk = currentTable.getForeignKeyMetadata().stream()
                .filter(key -> !key.fkTableName().equals(previousTable.getTableName()))
                .collect(Collectors.toMap(ForeignKeyMetadata::pkColumnName, a -> ColumnStatus.UNRESOLVED));

        Map<String, ColumnStatus> resolvedExt = currentTable.getExportedRelationships().stream()
                .filter(rel -> !rel.foreignTableName().equals(previousTable.getTableName()))
                .collect(Collectors.toMap(ExportedRelationshipMetadata::foreignTableName, a -> ColumnStatus.UNRESOLVED));

        var fkeys = currentTable.getForeignKeyMetadata().stream()
                .filter(key -> !key.fkTableName().equals(previousTable.getTableName()))
                .toList();

        var extRelationships = currentTable.getExportedRelationships().stream()
                .filter(rel -> !rel.foreignTableName().equals(previousTable.getTableName()))
                .toList();

        while (resolvedFk.containsValue(ColumnStatus.UNRESOLVED) || resolvedExt.containsValue(ColumnStatus.UNRESOLVED)) {

            Pair<Pair<String, String>, ColumnKeyStatus> selectionResult =
                    selectRelationshipToResolve(fkeys, extRelationships, resolvedFk, resolvedExt);

            String nextTableName = selectionResult.getFirst().getFirst();
            String columnName = selectionResult.getFirst().getSecond();

            // Migrate that relationship
            TableMetaData nextMetadata = storage.getFullJsonSchema().get(nextTableName).getSecond();
            printJoinInfo(currentTable, nextMetadata);
            String chosenCommand = selectCommand(nextTableName);
            switch (MigrationCommands.valueOf(chosenCommand)) {
                case EMBED -> {
                    var referenceType = defineRelationship();
                    var ebedding = recursiveStepIn(nextMetadata, currentTable);
                    boolean leavePk = confirmation("Do you want to keep primary key?");
                    var cleanJson = jsonSchemaFormatter.jsonTableToEmbed(ebedding, leavePk, referenceType);
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            ((ObjectNode) props).set(columnName, cleanJson);
                        } else {
                            ((ObjectNode) props).set(nextTableName, cleanJson);
                        }
                    }
                }
                case OMIT -> {
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            ((ObjectNode) props).remove(columnName);
                        } else {
                            ((ObjectNode) props).remove(nextTableName);
                        }
                    }
                }
                case REFERENCE -> {
                    var referenceType = defineRelationship();
                    var reference = jsonSchemaFormatter.getReferenceNode(nextMetadata, referenceType);
                    if (props instanceof ObjectNode) {
                        if (selectionResult.getSecond() == ColumnKeyStatus.FK) {
                            ((ObjectNode) props).set(columnName, reference);
                        } else {
                            ((ObjectNode) props).set(nextTableName, reference);
                        }
                    }
                }

                default -> { }
            }

            if (selectionResult.getSecond().equals(ColumnKeyStatus.FK)) {
                resolvedFk.replace(columnName, ColumnStatus.fromCommand(MigrationCommands.valueOf(chosenCommand)));
            }
            if (selectionResult.getSecond().equals(ColumnKeyStatus.EXT)) {
                resolvedExt.replace(nextTableName, ColumnStatus.fromCommand(MigrationCommands.valueOf(chosenCommand)));
            }
        }

        return result;
    }


    private RelationshipType defineRelationship() {
        List<SelectorItem<String>> relationshipTypeItems = Arrays.stream(RelationshipType.values())
                .map(type -> SelectorItem.of(type.name(), type.name()))
                .toList();

        var selectedRelationshipContext = selectSingleItem(relationshipTypeItems, "Define a relationship type");
        String selectedRelationship = selectedRelationshipContext.getValue().get();

        return RelationshipType.valueOf(selectedRelationship);
    }

    private void printJoinInfo(TableMetaData currentTable, TableMetaData nextTable) {
        var selectcount = queryLogRepository.getSelectData(currentTable.getTableName());
        var joincount = queryLogRepository.getJoinData(currentTable.getTableName(), nextTable.getTableName());
        System.out.println("SELECT DATA\n");
        System.out.printf("Table %s was selected %d times independently%n", currentTable.getTableName(), selectcount);

        System.out.println("JOIN DATA\n");
        System.out.printf("Tables are joined %d times%n", joincount);

        System.out.printf("Joins occur %f\n", (Float.valueOf(joincount) / Float.valueOf(selectcount)));
    }

    /** Runs command selection menu
     * @param tableName A table name to check whether it's referencable
     * @return Command name
     */
    private String selectCommand(String tableName) {
        var referencable = storage.getSelectedTableNames().contains(tableName);
        List<SelectorItem<String>> commandSelectItems = new ArrayList<>();
        commandSelectItems.add(SelectorItem.of(MigrationCommands.EMBED.name(), MigrationCommands.EMBED.name()));
        commandSelectItems.add(SelectorItem.of(MigrationCommands.OMIT.name(), MigrationCommands.OMIT.name()));
//        commandSelectItems.add(SelectorItem.of(MigrationCommands.SKIP.name(), MigrationCommands.SKIP.name()));
        if (referencable) {
            commandSelectItems.add(SelectorItem.of(MigrationCommands.REFERENCE.name(), MigrationCommands.REFERENCE.name()));
        }

        // Init choice menu
        var currentTableSelectContext =
                selectSingleItem(commandSelectItems, "Select relationship to resolve");

        return currentTableSelectContext.getValue().orElse(null);
    }

    private Pair<Pair<String, String>, ColumnKeyStatus>
    selectRelationshipToResolve(List<ForeignKeyMetadata> fkeys,
                                List<ExportedRelationshipMetadata> extRelationships,
                                Map<String, ColumnStatus> resolvedFk,
                                Map<String, ColumnStatus> resolvedExt
    ) {

        String columnItemTemplate = "%32s\t%5s\t[%15s]";
        List<SelectorItem<String>> relationResolveSelectItems = new ArrayList<>();

        Map<String, Pair<Pair<String, String>, ColumnKeyStatus>> returnMap = new HashMap<>();

        // Add select options for FK relationships
        for (ForeignKeyMetadata fkMeta : fkeys) {
            var label = columnItemTemplate.formatted(
                    fkMeta.pkColumnName(),
                    ColumnKeyStatus.FK,
                    resolvedFk.get(fkMeta.pkColumnName()).name()
            );

            var columnRecord = Pair.of(
                    Pair.of(fkMeta.fkTableName(), fkMeta.pkColumnName()),
                    ColumnKeyStatus.FK
            );

            returnMap.put(fkMeta.fkTableName(), columnRecord);
            relationResolveSelectItems.add(SelectorItem.of(label, fkMeta.fkTableName()));
        }

        // Add select options for EXTERNAL relationships
        for (ExportedRelationshipMetadata relationship : extRelationships) {
            var label = columnItemTemplate.formatted(
                    relationship.foreignTableName(),
                    ColumnKeyStatus.EXT,
                    resolvedExt.get(relationship.foreignTableName()).name()
            );
            var columnRecord = Pair.of(
                    Pair.of(relationship.foreignTableName(), relationship.foreignTableColumn()),
                    ColumnKeyStatus.EXT
            );
            returnMap.put(relationship.foreignTableName(), columnRecord);
            relationResolveSelectItems.add(SelectorItem.of(label, relationship.foreignTableName()));
        }

        // Init choice menu
        var currentTableSelectContext = selectSingleItem(relationResolveSelectItems, "Select relationship to resolve");
        var selectionResult = currentTableSelectContext.getValue().get();

        return returnMap.get(selectionResult);
    }

    private void printExternalReferencesInfo(TableMetaData metaData) {
        if (metaData.getExportedRelationships().size() < 1) return;
        System.out.println("There are several external references");
        String template = "# %s.%s <---- %s.%s";
        metaData.getExportedRelationships()
                .forEach(rel -> System.out.printf(
                        (template) + "%n", rel.sourceTableName(),
                        rel.sourceColumnName(),
                        rel.foreignTableName(),
                        rel.foreignTableColumn()
                ));
        System.out.println();
    }

    /** Prints table displaying basic table information
     * @param table Metadata for a table to be displayed
     */
    private void showTable(TableMetaData table) {
        List<ColumnMetadata> columnMetadata = table.getColumnMetadata();

        int rows = columnMetadata.size() + 1;
        int cols = 3; // TEMP

        String[][] data = new String[rows][cols];

        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);

        String[] header = {"Name", "Type", "Key?"};
        System.arraycopy(header, 0, data[0], 0, cols);

        for (int i = 1; i < rows; i++) {
            var columnName = columnMetadata.get(i - 1).columnName();
            data[i][0] = columnName;
            tableBuilder.on(at(i, 0)).addAligner(SimpleHorizontalAligner.center);
            tableBuilder.on(at(i, 0)).addAligner(SimpleVerticalAligner.middle);

            data[i][1] = columnMetadata.get(i - 1).typename();
            tableBuilder.on(at(i, 1)).addAligner(SimpleHorizontalAligner.center);
            tableBuilder.on(at(i, 1)).addAligner(SimpleVerticalAligner.middle);

            if (table.getForeignKeyMetadata().stream()
                    .anyMatch(fk -> fk.pkColumnName().equals(columnName))
            ) {
                data[i][2] = "FK";
            } else if (table.getPrimaryKeyMetadata().stream()
                    .anyMatch(pk -> pk.column().equals(columnName))
            ) {
                data[i][2] = "PK";
            } else {
                data[i][2] = "-";
            }
            tableBuilder.on(at(i, 2)).addAligner(SimpleHorizontalAligner.center);
            tableBuilder.on(at(i, 2)).addAligner(SimpleVerticalAligner.middle);
        }

        System.out.println(
                tableBuilder
                        .addFullBorder(BorderStyle.fancy_light)
                        .build()
                        .render(getTerminal().getWidth())
        );
    }

    /**
     * Renders table selection menu.
     * It's mainly used to select current table for further operations
     * @param uiTables List of table records to render menu options
     * @return Selected table name
     */
    private String getCurrentTableName(Map<String, UiTableEntry> uiTables, Map<String, Boolean> migrationStatus) {
        // Init choice list
        // Mapping of <Label, Value> pairs for menu items
        String template = "%32s\t(FK: %d\tREFERENCED BY: %d) [%s]";
        List<SelectorItem<String>> items = new ArrayList<>();
        for (UiTableEntry item : uiTables.values()) {
            var label = template.formatted(
                    item.getTableName(),
                    item.getForeignKeyCount(),
                    item.getExternalReferenceCount(),
                    migrationStatus.get(item.getTableName()) ? "MIGRATED" : "NOT MIGRATED"
            );
            items.add(SelectorItem.of(label, item.getTableName()));
        }

        // Add exit choice
        items.add(SelectorItem.of("<*> " + ReservedCommands.RETURN.name(), ReservedCommands.RETURN.name()));

        // Init choice menu
        var currentTableSelectContext = selectSingleItem(items, "Select table to migrate");

        // Get result
        return currentTableSelectContext.getValue().orElse(null);
    }

    /**
     * Renders single select menu
     * @param items Items to add as select options
     * @param prompt Prompt for single select menu
     * @return componentContext to retrieve selected item
     */
    private SingleItemSelector.SingleItemSelectorContext<String, SelectorItem<String>> selectSingleItem(List<SelectorItem<String>> items, String prompt) {
        SingleItemSelector<String, SelectorItem<String>> singleItemSelector =
                new SingleItemSelector<>(getTerminal(), items, prompt, null);

        singleItemSelector.setMaxItems(maxItems);
        singleItemSelector.setResourceLoader(getResourceLoader());
        singleItemSelector.setTemplateExecutor(getTemplateExecutor());
        return singleItemSelector.run(SingleItemSelector.SingleItemSelectorContext.empty());
    }

    /** Renders multi-select menu
     * @param tableItems Items to add as a select options
     * @param prompt Propmt for input
     * @return List of selected items
     */
    private List<String> selectMultiItem(List<SelectorItem<String>> tableItems, String prompt) {
        MultiItemSelector<String, SelectorItem<String>> tableSelectorComponent = new MultiItemSelector<>(
                getTerminal(),
                tableItems,
                prompt,
                null
        );
        tableSelectorComponent.setMaxItems(maxItems);
        tableSelectorComponent.setResourceLoader(getResourceLoader());
        tableSelectorComponent.setTemplateExecutor(getTemplateExecutor());
        var tableSelectorContext = tableSelectorComponent.run(MultiItemSelector.MultiItemSelectorContext.empty());

        return tableSelectorContext.getValues();
    }

    /** Renders string input
     * @param prompt Prompt for input
     * @param dflt Default value
     * @return Input context for result retrieval
     */
    private StringInput.StringInputContext getStringInput(String prompt, String dflt) {
        StringInput schemaInputComponent = new StringInput(getTerminal(), prompt, dflt);
        schemaInputComponent.setResourceLoader(getResourceLoader());
        schemaInputComponent.setTemplateExecutor(getTemplateExecutor());
        return schemaInputComponent.run(StringInput.StringInputContext.empty());
    }

    /** Renders confirmation menu
     * @param prompt Prompt for confirmation menu
     * @return Yes/No
     */
    private boolean confirmation(String prompt) {
        ConfirmationInput confirmationComponent = new ConfirmationInput(getTerminal(), prompt);
        confirmationComponent.setResourceLoader(getResourceLoader());
        confirmationComponent.setTemplateExecutor(getTemplateExecutor());
        ConfirmationInput.ConfirmationInputContext context = confirmationComponent.run(ConfirmationInput.ConfirmationInputContext.empty());
        return context.getResultValue();
    }



    public enum MigrationCommands { EMBED, OMIT, SKIP, REFERENCE }

    private enum ReservedCommands {RETURN}

    public static CellMatcher at(final int theRow, final int col) {
        return (row, column, model) -> row == theRow && column == col;
    }

    private void clear() {
        getTerminal().puts(InfoCmp.Capability.clear_screen);
    }

}
