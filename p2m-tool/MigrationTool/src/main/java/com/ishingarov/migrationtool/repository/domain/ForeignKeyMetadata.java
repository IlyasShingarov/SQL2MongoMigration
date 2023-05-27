package com.ishingarov.migrationtool.repository.domain;

public record ForeignKeyMetadata(
        String fkTableName,
        String fkColumnName,
        String pkTableName,
        String pkColumnName
){ };
