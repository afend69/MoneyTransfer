package processing;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.fasterxml.jackson.databind.util.JSONPObject;
import io.vertx.core.json.JsonObject;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

public class H2XaDatabaseManager implements DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(H2XaDatabaseManager.class);
    private static final String DIS_DB_CHANGELOG_XML = "db.datamodel.changes.xml";
    private final String h2ConnectionString;
    private static final String H2_USER = "sa";
    private static final String H2_PASSWRD = "sa";
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private final AtomikosDataSourceBean dbPool;

    H2XaDatabaseManager(String dbName) {
        this.h2ConnectionString = String.format("jdbc:h2:./db/h2/%s;AUTO_RECONNECT=TRUE;MVCC=true", dbName.toLowerCase());
        this.dbPool = createH2DisDatabaseXaConnectionsPool();
        initDb();
    }

    private AtomikosDataSourceBean createH2DisDatabaseXaConnectionsPool() {
        AtomikosDataSourceBean databaseXaPool = new AtomikosDataSourceBean();
        databaseXaPool.setXaDataSourceClassName("org.h2.jdbcx.JdbcDataSource");

        Properties properties = new Properties();
        properties.setProperty("url", h2ConnectionString);
        properties.setProperty("user", H2_USER);
        properties.setProperty("password", H2_PASSWRD);

        databaseXaPool.setXaProperties(properties);
        databaseXaPool.setUniqueResourceName("BANK-DB-" + System.nanoTime());
        databaseXaPool.setPoolSize(300);
        databaseXaPool.setBorrowConnectionTimeout(20000);

        return databaseXaPool;
    }

    @Override
    public void initDb() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
        try (Connection conn = DriverManager.
                getConnection(h2ConnectionString, H2_USER, H2_PASSWRD)) {

            @SuppressWarnings("ConstantConditions")
            File settingsFile = Arrays.stream(new File(".")
                    .listFiles((file, s) -> DIS_DB_CHANGELOG_XML.equals(s)))
                    .findFirst()
                    .orElseGet(() -> Arrays.stream(new File("..")
                            .listFiles((file, s) -> DIS_DB_CHANGELOG_XML.equals(s)))
                            .findFirst().orElse(null)
                    );

            if (settingsFile == null) {
                return;
            }
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase(settingsFile.getAbsolutePath(), new FileSystemResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());

        } catch (SQLException | LiquibaseException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public String createAccount(String bankCode) {
        try (Connection conn = dbPool.getConnection();
             Statement s = conn.createStatement()) {

            String sql = String.format("SELECT COUNT(*) FROM %s", "ACCOUNT");
            s.execute(sql);
            int accountsQty = 0;
            try (ResultSet rs = s.getResultSet()) {
                while (rs.next()) {
                    accountsQty = rs.getInt(1);
                }
            }
            String generatedIban = String.format("%s_%08d", bankCode, accountsQty + 1);

            sql = String.format("INSERT INTO ACCOUNT (IBAN,VALUE,LAST_UPDATE_DATETIME) VALUES ('%s', '%s', '%s')",
                    generatedIban,
                    BigDecimal.valueOf(0.00),
                    dateFormatter.format(new Date())
            );
            s.execute(sql);
            return generatedIban;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return "";
    }

    @Override
    public void clear() throws SQLException {
        try (Connection conn = dbPool.getConnection();
             Statement s = conn.createStatement()) {

            String sql = String.format("TRUNCATE TABLE %s", "ACCOUNT");
            s.execute(sql);
        }
    }

    @Override
    public boolean withdraw(String account, BigDecimal value) {
        try (Connection conn = dbPool.getConnection();
             Statement s = conn.createStatement()) {

            String sql = String.format("UPDATE ACCOUNT SET VALUE = CASEWHEN(VALUE-%s < 0, '', VALUE-%s) WHERE IBAN = '%s'", value, value, account);
            return s.executeUpdate(sql) == 1;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void deposit(String account, BigDecimal value) throws SQLException {
        try (Connection conn = dbPool.getConnection();
             Statement s = conn.createStatement()) {

            String sql = String.format("UPDATE ACCOUNT SET VALUE = VALUE + %s WHERE IBAN = '%s'", value, account);
            s.execute(sql);
        }
    }

    @Override
    public String status(String iban) {
        JsonObject result = new JsonObject();
        try (Connection conn = dbPool.getConnection();
             Statement s = conn.createStatement()) {

            String sql = String.format("SELECT IBAN, VALUE FROM ACCOUNT WHERE IBAN = '%s'", iban);
            s.execute(sql);

            try (ResultSet rs = s.getResultSet()) {
                JsonObject account = new JsonObject();
                while (rs.next()) {
                    account.put("iban", rs.getString("IBAN"));
                    account.put("amount", rs.getString("VALUE"));
                }
                result.put("value", account);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return result.toString();
    }
}
