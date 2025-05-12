package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {

    private static final String DB_URL = "jdbc:mysql://mysql-pz-programowanie-zespolowe.j.aivencloud.com:23083/pzdb?ssl-mode=REQUIRED";
    private static final String DB_USER = "avnadmin";
    private static final String DB_PASS = "AVNS_xldj6Pywht7u1kl_kgh";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }
}
