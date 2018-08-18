package simple.bank;

import java.math.BigDecimal;

public interface Bank {
    String openAccount();
    void addMoneyToAccount(String iban, BigDecimal value);
    void transferMoney(String sourceIban, String targetIban, BigDecimal value);
    String getAccountStatus(String iban);
    String getAllAccounts();
    void deleteAllAccounts();

    void start();
    void stop();
}
