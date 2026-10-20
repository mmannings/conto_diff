package org.gomma.diff;

import org.gomma.diff.utils.DataBaseHandler;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LongChangeValueTest {
    @Test
    void storesReportedLengthInWorkingTableValue4() throws Exception {
        char[] characters = new char[2943];
        Arrays.fill(characters, 'x');
        String value = new String(characters);
        try (DataBaseHandler database = new DataBaseHandler()) {
            new DiffExecutor(database).setupRepository();
            try (PreparedStatement insert = database.prepareStatement("INSERT INTO "
                    + Globals.WORKING_TABLE + " (actionMD5,change_action,value4) VALUES ('hash','test',?)")) {
                insert.setString(1, value);
                assertEquals(1, insert.executeUpdate());
            }
            try (PreparedStatement select = database.prepareStatement("SELECT value4 FROM "
                    + Globals.WORKING_TABLE + " WHERE actionMD5='hash'");
                    ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(value, rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void preservesValuesLongerThanTheReported2000CharacterLimit() throws Exception {
        verifyRoundTrip(2943);
    }

    @Test
    void preservesValuesBeyondTheOther6000CharacterLimit() throws Exception {
        verifyRoundTrip(12000);
    }

    private void verifyRoundTrip(int length) throws Exception {
        char[] characters = new char[length];
        Arrays.fill(characters, 'x');
        String value = new String(characters);
        String columns = "value1,value2,value3,value4,value5,value6";
        try (DataBaseHandler database = new DataBaseHandler()) {
            new DiffExecutor(database).setupRepository();
            try (PreparedStatement insert = database.prepareStatement(
                    "INSERT INTO " + Globals.VERSION_TABLE
                    + " (version,type,base_type,values_md5," + columns + ")"
                    + " VALUES ('old','test','test','hash',?,?,?,?,?,?)")) {
                for (int i = 1; i <= 6; i++) insert.setString(i, value);
                assertEquals(1, insert.executeUpdate());
            }
            // Exercise the INSERT ... SELECT operations used to move values
            // between intermediate and result tables, not only direct inserts.
            String source = Globals.VERSION_TABLE;
            for (String target : new String[]{Globals.WORKING_TABLE,
                    Globals.HIGH_LEVEL_RESULT_TABLE, Globals.LOW_LEVEL_RESULT_TABLE}) {
                assertEquals(1, database.executeDml("INSERT INTO " + target
                        + " (actionMD5,change_action," + columns + ") SELECT 'hash','test',"
                        + columns + " FROM " + source));
                source = target;
            }
            for (String table : new String[]{Globals.VERSION_TABLE, Globals.WORKING_TABLE,
                    Globals.HIGH_LEVEL_RESULT_TABLE, Globals.LOW_LEVEL_RESULT_TABLE}) {
                // Value columns remain comparable and indexed; a CLOB would
                // change the SQL semantics relied on by the diff rules.
                try (PreparedStatement select = database.prepareStatement("SELECT " + columns
                        + " FROM " + table + " WHERE value1=? AND value1=value4")) {
                    select.setString(1, value);
                    try (ResultSet rows = select.executeQuery()) {
                        assertTrue(rows.next(), table);
                        for (int i = 1; i <= 6; i++) assertEquals(value, rows.getString(i), table + ".value" + i);
                        assertFalse(rows.next(), table);
                    }
                }
            }
        }
    }
}
