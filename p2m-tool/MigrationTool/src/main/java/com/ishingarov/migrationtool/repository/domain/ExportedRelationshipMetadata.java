package com.ishingarov.migrationtool.repository.domain;

public record ExportedRelationshipMetadata(
        String sourceTableName,
        String sourceColumnName,
        String foreignTableName,
        String foreignTableColumn
) {
}
