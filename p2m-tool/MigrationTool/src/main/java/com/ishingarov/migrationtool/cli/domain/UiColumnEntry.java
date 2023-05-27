package com.ishingarov.migrationtool.cli.domain;

import com.ishingarov.migrationtool.cli.MigrationController;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiColumnEntry {

    private String columnName;
    private ColumnKeyStatus key;
    @Builder.Default
    private ColumnStatus migrationStatus = ColumnStatus.UNRESOLVED;

    public enum ColumnKeyStatus {PK, FK, NONE, EXT}

    public enum ColumnStatus {
        UNRESOLVED, OMITTED, EMBEDDED, REFERENCED, SKIPPED;

        public static ColumnStatus fromCommand(MigrationController.MigrationCommands command) {
            return switch (command) {
                case EMBED -> EMBEDDED;
                case OMIT -> OMITTED;
                case SKIP -> SKIPPED;
                case REFERENCE -> REFERENCED;
            };
        }
    }
}
