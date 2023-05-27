package com.ishingarov.migrationtool.repository.domain;

public record ImportedRelationshipMetadata(String sourceColumn, String referencedTable, String referencedColumn, RelationshipType type) {
}
