package com.ishingarov.migrationtool.format;

import org.springframework.data.util.Pair;

import java.util.List;

public record EmbedArrProp(String name,
                           String datatype,
                           Pair<String, String> soruce,
                           List<String> join) {
}
