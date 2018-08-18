package simple.bank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleBank implements Bank {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBank.class);

    private RestServer rest;
    private final ConcurrentHashMap<String, BankAccount> accounts = new ConcurrentHashMap<>();

    @Override
    public void start() {
        logger.info("SimpleBank is starting...");
        rest = new RestServer(this);
        rest.start();
        logger.info("SimpleBank is ready!");
    }

    @Override
    public void stop() {
        logger.info("Stopping SimpleBank...");
        if (rest != null) {
            rest.stop();
        }
        logger.info("SimpleBank was stopped!");
        System.exit(0);
    }

    @Override
    public String openAccount() {
        logger.info("Account is opening...");
        String generatedIban = String.format("BANK_%08d", accounts.size() + 1);
        accounts.put(generatedIban, new Account());
        logger.info(String.format("Account [%s] is opened!", generatedIban));
        return String.format("{\"value\":{\"iban\":\"%s\"}}", generatedIban);
    }

    @Override
    public void addMoneyToAccount(String iban, BigDecimal value) {
        logger.info("Deposit is starting...");
        if (iban == null || value.compareTo(BigDecimal.valueOf(0.0)) == 0) {
            logger.error("Wrong input data for deposit!");
            return;
        }
        BankAccount account = accounts.get(iban);
        if (account != null) {
            account.deposit(value);
        }
        logger.info("Deposit [{}] to account [{}] is completed!", value, iban);
    }

    @Override
    public void transferMoney(String sourceIban, String targetIban, BigDecimal value) {
        logger.info("Transfer is starting...");
        if (sourceIban == null || targetIban == null || value.compareTo(BigDecimal.valueOf(0.0)) == 0) {
            logger.error("Wrong input data for transfer!");
            return;
        }
        if (sourceIban.equals(targetIban)) {
            logger.warn("Source and target accounts are the same! Transfer will interrupted!");
            return;
        }
        BankAccount sourceAccount = accounts.get(sourceIban);
        BankAccount targetAccount = accounts.get(targetIban);
        if (sourceAccount != null && targetAccount != null) {
            if (sourceAccount.withdraw(value)) {
                targetAccount.deposit(value);
                logger.info("Transfer [{}] from [{}] to [{}] is completed!", value, sourceIban, targetIban);
            } else {
                logger.error("Not enough money on account [{}]! Transfer will interrupted!", sourceIban);
            }
        } else {
            logger.error("Source or Target IBAN is wrong! Transfer will interrupted!");
        }
    }

    @Override
    public String getAccountStatus(String iban) {
        logger.info("Getting account status is starting...");
        if (iban == null) {
            logger.error("IBAN is empty! Getting status will interrupted!");
            return "{}";
        }
        BankAccount account = accounts.get(iban);
        logger.info("Getting account status is completed!");
        if (account != null) {
            return String.format("{\"value\":{\"iban\":\"%s\",\"amount\":\"%s\"}}", iban, account.status());
        }
        return "{}";
    }

    @Override
    public String getAllAccounts() {
        logger.info("Reading all accounts...");
        Set<String> accountsList = new HashSet<>();
        accounts.forEach((iban, account) ->
               accountsList.add(String.format("{\"iban\":\"%s\",\"amount\":\"%s\"}", iban, account.status()))
        );
        logger.info("Reading all accounts is completed!");
        return String.format("{\"value\":%s, \"@odata.count\":%s}", accountsList.toString(), accountsList.size());
    }

    @Override
    public void deleteAllAccounts() {
        logger.info("Deleting all accounts...");
        accounts.clear();
        logger.info("Deleting all accounts is completed!");
    }

    public static void main(String[] args) {
        SimpleBank bank = new SimpleBank();
        bank.start();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
