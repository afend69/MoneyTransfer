package processing;

import java.math.BigDecimal;
import java.sql.SQLException;

public interface DatabaseManager {
    void initDb();
    String createAccount(String bankCode);
    void clear() throws SQLException;
    boolean withdraw(String account, BigDecimal value);
    void deposit(String account, BigDecimal value) throws SQLException;
    String status(String iban);
}
