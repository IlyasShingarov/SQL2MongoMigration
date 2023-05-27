package com.ishingarov.migrationtool.format;

public record Ref(
        String name,
        String source,
        String pkName,
        PropType type
) {
}


