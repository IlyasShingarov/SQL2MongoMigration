package com.ishingarov.migrationtool.cli.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ishingarov.migrationtool.repository.domain.TableMetaData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UiTableEntry {

    private String tableName;
    private TableMetaData metaData;
    private ObjectNode json;

    private int foreignKeyCount;
    private int externalReferenceCount;

    public UiTableEntry(Pair<ObjectNode, TableMetaData> pair) {
        this.tableName = pair.getSecond().getTableName();
        this.metaData = pair.getSecond();
        this.json = pair.getFirst();
        this.foreignKeyCount = metaData.getForeignKeyMetadata().size();
        this.externalReferenceCount = metaData.getExportedRelationships().size();
        this.columns = metaData.getColumnMetadata().stream()
                .map(col -> {
                            var key = UiColumnEntry.ColumnKeyStatus.NONE;
                            if (metaData.getPrimaryKeyMetadata().stream()
                                    .anyMatch(pk -> pk.column().equals(col.columnName()))
                            ) {
                                key = UiColumnEntry.ColumnKeyStatus.PK;
                            }
                            if (metaData.getForeignKeyMetadata().stream()
                                    .anyMatch(pk -> pk.pkColumnName().equals(col.columnName()))
                            ) {
                                key = UiColumnEntry.ColumnKeyStatus.FK;
                            }

                            return UiColumnEntry.builder()
                                    .columnName(col.columnName())
                                    .key(key)
                                    .build();
                }).collect(Collectors.toMap(UiColumnEntry::getColumnName, Function.identity()));
    }

    @Builder.Default
    private Map<String, UiColumnEntry> columns = new HashMap<>();

}

