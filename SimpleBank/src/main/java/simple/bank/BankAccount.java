package simple.bank;

import java.math.BigDecimal;

public interface BankAccount {
    boolean withdraw(BigDecimal value);
    void deposit(BigDecimal value);
    BigDecimal status();
}
