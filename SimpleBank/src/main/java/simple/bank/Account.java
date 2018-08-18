package simple.bank;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

public class Account implements BankAccount {
    private final AtomicReference<BigDecimal> amountRef = new AtomicReference<>();

    Account() {
        this.amountRef.set(BigDecimal.valueOf(0.00));
    }

    @Override
    public boolean withdraw(BigDecimal value) {
        BigDecimal amount = amountRef.get();
        if (amount.compareTo(value) >= 0) {
            for (;;) {
                amount = amountRef.get();
                if (amountRef.compareAndSet(amount, amount.subtract(value)))
                    return true;
            }
        }
        return false;
    }

    @Override
    public void deposit(BigDecimal value) {
        for (;;) {
            BigDecimal amount = amountRef.get();
            if (amountRef.compareAndSet(amount, amount.add(value)))
                return;
        }
    }

    @Override
    public BigDecimal status() {
        return amountRef.get().setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}
