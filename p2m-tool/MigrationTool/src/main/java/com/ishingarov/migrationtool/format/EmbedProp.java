package com.ishingarov.migrationtool.format;

import org.springframework.data.util.Pair;

public record EmbedProp(
        String name,
        String datatype,
        Pair<String, String> soruce
) {
}
