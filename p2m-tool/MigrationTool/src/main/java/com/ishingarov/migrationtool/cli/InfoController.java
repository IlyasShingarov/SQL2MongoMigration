package com.ishingarov.migrationtool.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ishingarov.migrationtool.repository.domain.ColumnMetadata;
import com.ishingarov.migrationtool.storage.MetadataStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.table.*;

import java.util.List;

@ShellComponent
@RequiredArgsConstructor
public class InfoController {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final MetadataStorage storage;

    @ShellMethod(key = "show json", value = "Show JSON intermediate schema for a table", group = "Info")
    public String showJsonTable(String table) throws JsonProcessingException {
        return objectMapper.writeValueAsString(storage.getFullJsonSchema().get(table).getFirst());
    }

    @ShellMethod(key = "show tables selected", value = "Show selected tables", group = "Info")
    public List<String> showSelectedTables() {
        return storage.getSelectedTableNames();
    }

    @ShellMethod(key = "show tables all", value = "Show all tables", group = "Info")
    public List<String> showAllTables() {
        return storage.getTableNames();
    }

    @ShellMethod(key = "show meta", value = "Show table metadata", group = "Info")
    public String showTableMetadata(String table) {
        var meta = storage.getFullJsonSchema().get(table).getSecond();
        return meta != null ? meta.toString() : "";
    }

    @ShellMethod(key = "show result", value = "Show result", group = "Info")
    public String showResultTable(String table) throws JsonProcessingException {
        var json = storage.getResultJsonSchema().get(table);
        return json != null ? objectMapper.writeValueAsString(json) : "";
    }

    // TODO: Test +  refactor
    @ShellMethod(key = "show table", value = "Showcase Table rendering", group = "Info")
    public Table showTable(String table) {
        var tblmeta = storage.getFullJsonSchema().get(table).getSecond();

        if (tblmeta != null) {
            List<ColumnMetadata> columnMetadata = tblmeta.getColumnMetadata();
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

                if (tblmeta.getForeignKeyMetadata().stream()
                        .anyMatch(fk -> fk.pkColumnName().equals(columnName))
                ) {
                    data[i][2] = "FK";
                } else if (tblmeta.getPrimaryKeyMetadata().stream()
                        .anyMatch(pk -> pk.column().equals(columnName))
                ) {
                    data[i][2] = "PK";
                } else {
                    data[i][2] = "-";
                }
                tableBuilder.on(at(i, 2)).addAligner(SimpleHorizontalAligner.center);
                tableBuilder.on(at(i, 2)).addAligner(SimpleVerticalAligner.middle);
            }

            return tableBuilder.addFullBorder(BorderStyle.fancy_light).build();
        }

        TableModel model = new ArrayTableModel(new String[][] {new String[]{"There's no such table as " + table}});
        TableBuilder builder = new TableBuilder(model);
        return builder.addFullBorder(BorderStyle.fancy_light_quadruple_dash).build();
    }

    public static CellMatcher at(final int theRow, final int col) {
        return new CellMatcher() {
            @Override
            public boolean matches(int row, int column, TableModel model) {
                return row == theRow && column == col;
            }
        };
    }

}
