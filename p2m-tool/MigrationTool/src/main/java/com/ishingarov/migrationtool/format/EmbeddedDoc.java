package com.ishingarov.migrationtool.format;

public record EmbeddedDoc(
        String name,
        String source,
        PropType type,
        MigrationSchema schema) {
}
