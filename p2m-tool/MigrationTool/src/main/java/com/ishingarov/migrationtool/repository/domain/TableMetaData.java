package com.ishingarov.migrationtool.repository.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class TableMetaData {
    private final String tableName;
    private final List<ForeignKeyMetadata> foreignKeyMetadata;
    private final List<PrimaryKeyMetadata> primaryKeyMetadata;
    private final List<ColumnMetadata> columnMetadata;
    private final List<ImportedRelationshipMetadata> importedRelationships;
    private final List<ExportedRelationshipMetadata> exportedRelationships;

    private TableType tableType;

    public TableMetaData(String tableName) {
        this.tableName = tableName;
        this.foreignKeyMetadata = new ArrayList<>();
        this.primaryKeyMetadata = new ArrayList<>();
        this.columnMetadata = new ArrayList<>();
        this.importedRelationships = new ArrayList<>();
        this.exportedRelationships = new ArrayList<>();
    }

}
