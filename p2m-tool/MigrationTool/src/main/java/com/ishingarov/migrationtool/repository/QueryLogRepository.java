package com.ishingarov.migrationtool.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class QueryLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public int getJoinData(String firstTable, String secondTable) {
        String likestmt = "%%%s%% %%join%% %%%s%%".formatted(firstTable, secondTable);
        String query = """
                        SELECT sum(calls)
                        FROM pg_stat_statements
                        WHERE query like ?
                    """;
        Integer joinCount = jdbcTemplate
                .queryForObject(query, Integer.class, likestmt);

        return joinCount != null ? joinCount : 0;
    }

    public int getSelectData(String table) {
        String likeSelectStmt = "select %%from %%%s%%";
        String likeJoinStmt = "%%%s%%join%%";
        String query = """
                    SELECT sum(calls)
                    FROM pg_stat_statements
                    WHERE 
                        query like ? 
                    AND
                        query not like ?
                """;
        Integer selectCount = jdbcTemplate
                .queryForObject(query, Integer.class, likeSelectStmt, likeJoinStmt);

        return selectCount != null ? selectCount : 0;
    }
}
