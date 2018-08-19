package processing;

import com.atomikos.icatch.jta.UserTransactionImp;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.SystemException;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessingCenter implements Processing {
    static {
        String logFileName;
        try {
            logFileName = new File(System.getProperty("user.dir"), "ProcessingCenter@" + InetAddress.getLocalHost().getHostName() + ".log").getAbsolutePath();
        } catch (UnknownHostException e) {
            logFileName = new File(System.getProperty("user.dir"), "ProcessingCenter.log").getAbsolutePath();
        }
        ThreadContext.put("ProcessingCenter.logs", logFileName);
    }

    private static final Logger logger = LoggerFactory.getLogger(ProcessingCenter.class);
    private static final UserTransactionImp utx = new UserTransactionImp();
    private final ConcurrentHashMap<String, Bank> banks = new ConcurrentHashMap<>();
    private SslRestServer rest;


    public ProcessingCenter() {
        //
    }

    public String getBankCodeFromResponse(String response) {
        JsonObject json = new JsonObject(response);
        JsonObject account = json.getJsonObject("value");
        return account.getString("code");
    }

    public String getIbanFromResponse(String response) {
        JsonObject json = new JsonObject(response);
        JsonObject account = json.getJsonObject("value");
        return account.getString("iban");
    }

    @Override
    public String createBank() {
        logger.info("Creating Bank...");
        String generatedCode = String.format("BANK%02d", banks.size() + 1);
        banks.put(generatedCode, new BankImpl(generatedCode));
        logger.info("Bank [{}] was created!", generatedCode);
        return String.format("{\"value\":{\"code\":\"%s\"}}", generatedCode);
    }

    @Override
    public String createAccount(String bankCode) {
        if (bankCode == null) {
            logger.error("Bank Code is empty! Create Account will interrupted!");
            return "{}";
        }
        try {
            utx.begin();
            Bank bank = banks.get(bankCode);
            String iban = bank.createAccount();
            utx.commit();
            logger.info("Account [{}] is created!", getIbanFromResponse(iban));
            return iban;
        } catch (Exception e) {
            try {
                utx.rollback();
            } catch (SystemException se) {
                logger.error(se.getMessage(), se);
            }
            logger.error("Account creating at Bank [{}] is failed: {}", bankCode, e.getMessage());
        }
        return "{}";
    }

    @Override
    public void addMoneyToAccount(String iban, BigDecimal value) {
        if (iban == null || value.compareTo(BigDecimal.valueOf(0.0)) <= 0) {
            logger.error("Wrong input data! Add Money to Account will interrupted!");
            return;
        }
        try {
            utx.begin();
            String bankCode = iban.split("_")[0];
            Bank bank = banks.get(bankCode);
            bank.deposit(iban, value);
            utx.commit();
            logger.info("Adding [{}] to Account [{}] is OK!", value, iban);
        } catch (Exception e) {
            try {
                utx.rollback();
            } catch (SystemException se) {
                logger.error(se.getMessage(), se);
            }
            logger.error("Adding [{}] to Account [{}] is failed: {}", value, iban, e.getMessage());
        }
    }

    @Override
    public void transferMoney(String sourceIban, String targetIban, BigDecimal value) {
        if (sourceIban == null || targetIban == null || value.compareTo(BigDecimal.valueOf(0.0)) <= 0) {
            logger.error("Wrong input data! Transfer Money will interrupted!");
            return;
        }
        if (sourceIban.equals(targetIban)) {
            logger.error("Source and Target Accounts are the same! Transfer Money will interrupted!");
            return;
        }
        try {
            utx.begin();
            String sourceBankCode = sourceIban.split("_")[0];
            String targetBankCode = targetIban.split("_")[0];
            Bank sourceBank = banks.get(sourceBankCode);
            Bank targetBank = banks.get(targetBankCode);
            if (sourceBank.withdraw(sourceIban, value)) {
                targetBank.deposit(targetIban, value);
            }
            utx.commit();
            logger.info("Transfer [{}] from [{}] to [{}] is OK!", value, sourceIban, targetIban);
        } catch (Exception e) {
            try {
                utx.rollback();
            } catch (SystemException se) {
                logger.error(se.getMessage(), se);
            }
            logger.error("Transfer [{}] from [{}] to [{}] is failed: {}", value, sourceIban, targetIban, e.getMessage());
        }
    }

    @Override
    public String getAccountStatus(String iban) {
        if (iban == null) {
            logger.error("IBAN is empty! Get Account status will interrupted!");
            return "{}";
        }
        try {
            utx.begin();
            String bankCode = iban.split("_")[0];
            Bank bank = banks.get(bankCode);
            String status = bank.getAccountStatus(iban);
            utx.commit();
            return status;
        } catch (Exception e) {
            try {
                utx.rollback();
            } catch (SystemException se) {
                logger.error(se.getMessage(), se);
            }
            logger.error("Getting account [{}] status is failed: {}", iban, e.getMessage());
        }
        return "{}";
    }

    @Override
    public void deleteAllAccounts(String bankCode) {
        if (bankCode == null) {
            logger.error("Bank Code is empty! Delete All Accounts will interrupted!");
            return;
        }
        try {
            utx.begin();
            Bank bank = banks.get(bankCode);
            bank.deleteAllAccounts();
            utx.commit();
            logger.info("Deleting all accounts at Bank [{}] is OK!", bankCode);
        } catch (Exception e) {
            try {
                utx.rollback();
            } catch (SystemException se) {
                logger.error(se.getMessage(), se);
            }
            logger.info("Deleting all accounts at Bank [{}] is failed: {}", bankCode, e.getMessage());
        }
    }

    @Override
    public List<String> getAllBankCodes() {
        return new ArrayList<>(banks.keySet());
    }

    @Override
    public void start() {
        logger.info("Processing Center is starting...");
        this.rest = new SslRestServer(this);
        rest.start();
        logger.info("Processing Center is ready!");
    }

    @Override
    public void stop() {
        rest.stop();
        logger.info("Processing Center was stopped!");
        System.exit(0);
    }

    public static void main(String[] args) {
        ProcessingCenter p = new ProcessingCenter();
        p.start();

        String code1 = p.getBankCodeFromResponse(p.createBank());
        String code2 = p.getBankCodeFromResponse(p.createBank());

        String iban11 = p.getIbanFromResponse(p.createAccount(code1));
        String iban12 = p.getIbanFromResponse(p.createAccount(code1));
        String iban21 = p.getIbanFromResponse(p.createAccount(code2));
        String iban22 = p.getIbanFromResponse(p.createAccount(code2));

        p.addMoneyToAccount(iban11, BigDecimal.valueOf(10_000.00));
        p.addMoneyToAccount(iban12, BigDecimal.valueOf(10_000.00));
        p.addMoneyToAccount(iban21, BigDecimal.valueOf(10_000.00));
        p.addMoneyToAccount(iban22, BigDecimal.valueOf(10_000.00));

//        p.transferMoney(iban11, iban12, BigDecimal.valueOf(55.00));
//        p.transferMoney(iban11, iban21, BigDecimal.valueOf(45.00));
//        p.transferMoney(iban21, iban22, BigDecimal.valueOf(15.00));
//
//        logger.info(p.getAccountStatus(iban11));
//        logger.info(p.getAccountStatus(iban12));
//        logger.info(p.getAccountStatus(iban21));
//        logger.info(p.getAccountStatus(iban22));

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}