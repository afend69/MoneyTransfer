package processing;

import java.math.BigDecimal;
import java.sql.SQLException;

public interface Bank {
    String createAccount();
    boolean withdraw(String account, BigDecimal value);
    void deposit(String account, BigDecimal value) throws SQLException;
    String getAccountStatus(String iban);
    String getAllAccounts();
    void deleteAllAccounts() throws SQLException;
}