package log.parser;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class MariaDatabase {
    public static @Nullable Connection connect(String dest, String user, String passwd) {
        Properties connProperties = new Properties();
        connProperties.setProperty("user", user);
        connProperties.setProperty("password", passwd);

        try (Connection conn = DriverManager.getConnection(dest, connProperties)){
            return conn;
        } catch (SQLException e){
            System.out.println("Error in connecting to the database");
            return null;
        }
    }

    // test db connection
    public static void main(String[] args) {
        Connection conn = connect(
                "jdbc:mariadb://unity-db.securli.hk:3306",
                "unity-dbuser",
                "Marco@Unity=123");
    }
}
