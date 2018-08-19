package processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;

public class BankImpl implements Bank {
    private static final Logger logger = LoggerFactory.getLogger(BankImpl.class);

    private final DatabaseManager db;
    private final String code;

    BankImpl(String code) {
        this.code = code;
        this.db = new H2XaDatabaseManager(code);
    }

    @Override
    public String createAccount() {
        String generatedIban = db.createAccount(code);
        return String.format("{\"value\":{\"iban\":\"%s\"}}", generatedIban);
    }

    @Override
    public boolean withdraw(String account, BigDecimal value) {
        return db.withdraw(account, value);
    }

    @Override
    public void deposit(String account, BigDecimal value) throws SQLException {
        db.deposit(account, value);
    }

    @Override
    public String getAccountStatus(String iban) {
        return db.status(iban);
    }

    @Override
    public String getAllAccounts() {
        return null;
    }

    @Override
    public void deleteAllAccounts() throws SQLException {
        db.clear();
    }
}
