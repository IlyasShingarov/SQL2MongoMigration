public int getJoinData(String firstTable, String secondTable) {
    String likestmt = "%%%s%% %%join%% %%%s%%".formatted(firstTable, secondTable);
    String query = "SELECT sum(calls) 
                    FROM pg_stat_statements 
                    WHERE query LIKE ?";
    Integer joinCount = jdbcTemplate
            .queryForObject(query, Integer.class, likestmt);
    
    return joinCount != null ? joinCount : 0;
}

public int getSelectData(String table) {
    String likeSelectStmt = "select %%from %%%s%%";
    String likeJoinStmt = "%%%s%%join%%";
    String query = "SELECT sum(calls) 
                    FROM pg_stat_statements 
                    WHERE query LIKE ? AND query NOT LIKE ?";
    Integer selectCount = jdbcTemplate
            .queryForObject(query, Integer.class, likeSelectStmt, likeJoinStmt);
    
    return selectCount != null ? selectCount : 0;
}