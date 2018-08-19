import io.vertx.core.json.JsonObject;
import org.junit.*;
import processing.ProcessingCenter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class SslRestServerTest {
    private static ProcessingCenter p;

    @BeforeClass
    public static void testSetup() throws IOException {
        File dbFolder = new File(System.getProperty("user.dir"), "db");
        if (dbFolder.exists()) {
            deleteDbFolder(dbFolder.toPath());
        }
        p = new ProcessingCenter();
        p.start();
    }

    @SuppressWarnings("all")
    private static void deleteDbFolder(Path dbFolder) throws IOException {
        Files.walk(dbFolder)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @AfterClass
    public static void testCleanup() {
        if (p != null) {
            p.stop();
        }
    }

    @Before
    public void deleteAllAccounts() {
        List<String> banks = p.getAllBankCodes();
        banks.forEach(bankCode -> p.deleteAllAccounts(bankCode));
    }

    @Test
    public void addMoneyToAccountTest() {
        String bankCode = p.getBankCodeFromResponse(p.createBank());
        String iban = p.getIbanFromResponse(p.createAccount(bankCode));

        HashMap<String, Object> map = new HashMap<>();
        map.put("iban", iban);
        map.put("amount", 26.89);

        given().relaxedHTTPSValidation()
                .contentType("application/json")
                .body(map)
                .when()
                .post("https://localhost:4201/account/deposit")
                .then()
                .statusCode(200);

        String status = p.getAccountStatus(iban);
        JsonObject response = new JsonObject(status);
        JsonObject account = response.getJsonObject("value");

        String ibanInStatus = account.getString("iban");
        assertThat(ibanInStatus, containsString("BANK01_00000001"));

        BigDecimal amountInStatus = new BigDecimal(account.getString("amount"));
        Assert.assertEquals(BigDecimal.valueOf(26.89), amountInStatus);
    }

    @Test
    public void transferMoneyBetweenBanksTest() {
        String sourceBankCode = p.getBankCodeFromResponse(p.createBank());
        String sourceIban = p.getIbanFromResponse(p.createAccount(sourceBankCode));
        p.addMoneyToAccount(sourceIban, BigDecimal.valueOf(100.00));

        String targetBankCode = p.getBankCodeFromResponse(p.createBank());
        String targetIban = p.getIbanFromResponse(p.createAccount(targetBankCode));

        HashMap<String, Object> map = new HashMap<>();
        map.put("sourceIban", sourceIban);
        map.put("targetIban", targetIban);
        map.put("amount", 23.58);

        given().relaxedHTTPSValidation()
                .contentType("application/json")
                .body(map)
                .when()
                .post("https://localhost:4201/account/transfer")
                .then()
                .statusCode(200);

        String sourceAccountStatus = p.getAccountStatus(sourceIban);
        JsonObject response = new JsonObject(sourceAccountStatus);
        JsonObject sourceAccount = response.getJsonObject("value");
        BigDecimal sourceAccountAmount = new BigDecimal(sourceAccount.getString("amount"));

        Assert.assertEquals(BigDecimal.valueOf(76.42).setScale(2,BigDecimal.ROUND_HALF_UP), sourceAccountAmount);

        String targetAccountStatus = p.getAccountStatus(targetIban);
        response = new JsonObject(targetAccountStatus);
        JsonObject targetAccount = response.getJsonObject("value");
        BigDecimal targetAccountAmount = new BigDecimal(targetAccount.getString("amount"));

        Assert.assertEquals(BigDecimal.valueOf(23.58).setScale(2, BigDecimal.ROUND_HALF_UP), targetAccountAmount);
    }
}
