package com.ishingarov.migrationtool.storage;

import com.ishingarov.migrationtool.repository.domain.TableMetaData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class TableMigrationState {

    private Integer unresolvedDependencyCount;
    private Integer resolvedDependencyCount;

    private final TableMetaData tableMetaData;

    TableMigrationState(TableMetaData tableMetaData) {
        this.tableMetaData = tableMetaData;
    }
}
