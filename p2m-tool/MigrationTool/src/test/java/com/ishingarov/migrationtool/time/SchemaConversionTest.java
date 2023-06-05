//package com.ishingarov.migrationtool.time;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.ishingarov.migrationtool.cli.MigrationController;
//import com.ishingarov.migrationtool.cli.domain.UiColumnEntry;
//import com.ishingarov.migrationtool.cli.domain.UiTableEntry;
//import com.ishingarov.migrationtool.format.JsonSchemaFormatter;
//import com.ishingarov.migrationtool.repository.domain.*;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.util.Pair;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//@SpringBootTest
//public class SchemaConversionTest {
//
//    private final JsonSchemaFormatter jsonSchemaFormatter;
//    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
//
//    public SchemaConversionTest(JsonSchemaFormatter jsonSchemaFormatter) {
//        this.jsonSchemaFormatter = jsonSchemaFormatter;
//    }
//
//    @Test
//    public void test() {
//        TableMetaData metaData = new TableMetaData("TABLE_1");
//        metaData.getPrimaryKeyMetadata().add(new PrimaryKeyMetadata("COLUMN_1", "TABLE_!_PK"));
//        metaData.getColumnMetadata()
//                .addAll(List.of(
//                        new ColumnMetadata("COLUMN_1", "1", "VARCHAR", 1, 0),
//                        new ColumnMetadata("COLUMN_2", "1", "VARCHAR", 2, 0),
//                        new ColumnMetadata("COLUMN_3", "1", "VARCHAR", 3, 0),
//                        new ColumnMetadata("COLUMN_4", "1", "VARCHAR", 4, 0),
//                        new ColumnMetadata("COLUMN_5", "1", "VARCHAR", 5, 0),
//                        new ColumnMetadata("COLUMN_6", "1", "VARCHAR", 6, 0),
//                        new ColumnMetadata("COLUMN_7", "1", "VARCHAR", 7, 0)
//                ));
//        metaData.getExportedRelationships().addAll(List.of(
//                new ExportedRelationshipMetadata("TABLE_1", "COLUMN_1", "TABLE_2", "COLUMN_2"),
//                new ExportedRelationshipMetadata("TABLE_1", "COLUMN_1", "TABLE_3", "COLUMN_3")
//        ));
//        metaData.getImportedRelationships();
//        metaData.getForeignKeyMetadata();
//
//
//
//
//
//
//
//
//
//
//
//        long startTime = System.currentTimeMillis();
//
//
//
//
//        long endTime = System.currentTimeMillis();
//
//    }
//
//
//    private void migrateTableWrapper() {
//            String currentTable = "TABLE_1";
//            ObjectNode result = migrateTable();
//    }
//
//    private ObjectNode migrateTable(TableMetaData currentMetadata) {
//        ObjectNode result = jsonSchemaFormatter.metadataToJson(currentMetadata);
//
//        // Prepare state for table migration
//        Map<String, UiColumnEntry.ColumnStatus> resolvedFk = currentMetadata.getForeignKeyMetadata().stream()
//                .collect(Collectors.toMap(ForeignKeyMetadata::pkColumnName, a -> UiColumnEntry.ColumnStatus.UNRESOLVED));
//
//        Map<String, UiColumnEntry.ColumnStatus> resolvedExt = currentMetadata.getExportedRelationships().stream()
//                .collect(Collectors.toMap(ExportedRelationshipMetadata::foreignTableName, a -> UiColumnEntry.ColumnStatus.UNRESOLVED));
//
//        // Run while every relationship is resolved
//        while (resolvedFk.containsValue(UiColumnEntry.ColumnStatus.UNRESOLVED) || resolvedExt.containsValue(UiColumnEntry.ColumnStatus.UNRESOLVED)) {
//            Pair<Pair<String, String>, UiColumnEntry.ColumnKeyStatus> selectionResult = selectRelationshipToResolve(
//                    currentMetadata.getForeignKeyMetadata(),
//                    currentMetadata.getExportedRelationships(),
//                    resolvedFk,
//                    resolvedExt
//            );
//
//            // Unpack results
//            String nextTableName = selectionResult.getFirst().getFirst();
//            String columnName = selectionResult.getFirst().getSecond();
//            TableMetaData nextMetadata = storage.getFullJsonSchema().get(nextTableName).getSecond();
//
////            RelationshipType referenceType = defineRelationship(currentMetadata, nextMetadata);
//
//            String currentCommand = selectCommand(nextTableName);
//            switch (MigrationController.MigrationCommands.valueOf(currentCommand)) {
//                case EMBED -> {
//                    RelationshipType referenceType = defineRelationship();
//                    var docToEmbed = recursiveStepIn(nextMetadata, currentMetadata);
//
//                    boolean leavePk = confirmation("Do you want to keep primary key?");
//
//                    var cleanJson = jsonSchemaFormatter.jsonTableToEmbed(docToEmbed, leavePk, referenceType);
//
//                    var props = result.get("properties");
//                    if (props instanceof ObjectNode) {
//                        if (selectionResult.getSecond() == UiColumnEntry.ColumnKeyStatus.FK) {
//                            ((ObjectNode) props).remove(columnName);
//                            ((ObjectNode) props).set(columnName, cleanJson);
//                        } else {
//                            ((ObjectNode) props).set(nextTableName, cleanJson);
//                        }
//                    }
//                }
//                case OMIT -> {
//                    var props = result.get("properties");
//                    if (props instanceof ObjectNode) {
//                        if (selectionResult.getSecond() == UiColumnEntry.ColumnKeyStatus.FK) {
//                            ((ObjectNode) props).remove(columnName);
//                        } else {
//                            ((ObjectNode) props).remove(nextTableName);
//                        }
//                    }
//                }
//                case REFERENCE -> {
//                    RelationshipType referenceType = defineRelationship();
//                    var reference = jsonSchemaFormatter.getReferenceNode(nextMetadata, referenceType);
//
//                    var props = result.get("properties");
//                    if (props instanceof ObjectNode) {
//                        if (selectionResult.getSecond() == UiColumnEntry.ColumnKeyStatus.FK) {
//                            if (reference instanceof ObjectNode) {
//                                ((ObjectNode) reference).put("name", columnName);
//                            }
//                            if (reference instanceof ArrayNode) {
//                                ((ObjectNode) reference.get(0)).put("name", columnName);
//                            }
//                            ((ObjectNode) props).set(columnName, reference);
//                        } else {
//                            if (reference instanceof ObjectNode) {
//                                ((ObjectNode) reference).put("name", nextTableName);
//                            }
//                            if (reference instanceof ArrayNode) {
//                                ((ObjectNode) reference.get(0)).put("name", nextTableName);
//                            }
//                            ((ObjectNode) props).set(nextTableName, reference);
//                        }
//                    }
//                }
//                default -> { }
//            }
//        }
//
//        return result;
//    }
//}
