package processing;

import java.math.BigDecimal;
import java.util.List;

public interface Processing {
    String createBank();
    String createAccount(String bank);
    void addMoneyToAccount(String iban, BigDecimal value);
    void transferMoney(String sourceIban, String targetIban, BigDecimal value);
    String getAccountStatus(String iban);
    void deleteAllAccounts(String bank);
    List<String> getAllBankCodes();

    void start();
    void stop();
}
