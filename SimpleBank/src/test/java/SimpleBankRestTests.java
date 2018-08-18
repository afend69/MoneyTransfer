import io.vertx.core.json.JsonObject;
import org.junit.*;
import simple.bank.Bank;
import simple.bank.SimpleBank;

import java.math.BigDecimal;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

public class SimpleBankRestTests {
    private static Bank bank;

    @BeforeClass
    public static void testSetup() {
        bank = new SimpleBank();
        bank.start();
    }

    @AfterClass
    public static void testCleanup() {
        if (bank != null) {
            bank.stop();
        }
    }

    @Before
    public void cleanDatabase() {
        bank.deleteAllAccounts();
    }

    private String getIbanFromResponse(String response) {
        JsonObject json = new JsonObject(response);
        JsonObject account = json.getJsonObject("value");
        return account.getString("iban");
    }

    @Test
    public void openAccountRestTest() {
        given()
                .when()
                .put("http://localhost:4200/account/open")
                .then()
                .statusCode(200)
                .body("value.iban", containsString("BANK_00000001"));
    }

    @Test
    public void addMoneyToAccountTest() {
        String iban = getIbanFromResponse(bank.openAccount());
        HashMap<String, Object> map = new HashMap<>();
        map.put("iban", iban);
        map.put("amount", 26.89);

        given()
                .contentType("application/json")
                .body(map)
            .when()
                .post("http://localhost:4200/account/deposit")
                .then()
                .statusCode(200);

        String status = bank.getAccountStatus(iban);
        JsonObject response = new JsonObject(status);
        JsonObject account = response.getJsonObject("value");

        String ibanInStatus = account.getString("iban");
        assertThat(ibanInStatus, containsString("BANK_00000001"));

        BigDecimal amountInStatus = new BigDecimal(account.getString("amount"));
        Assert.assertEquals(BigDecimal.valueOf(26.89), amountInStatus);
    }

    @Test
    public void transferMoneyTest() {
        String targetIban = getIbanFromResponse(bank.openAccount());

        String sourceIban = getIbanFromResponse(bank.openAccount());
        BigDecimal payment = BigDecimal.valueOf(12.47);
        bank.addMoneyToAccount(sourceIban, payment);

        HashMap<String, Object> map = new HashMap<>();
        map.put("sourceIban", sourceIban);
        map.put("targetIban", targetIban);
        map.put("amount", 7.17);

        given()
                .contentType("application/json")
                .body(map)
                .when()
                .post("http://localhost:4200/account/transfer")
                .then()
                .statusCode(200);

        String sourceAccountStatus = bank.getAccountStatus(sourceIban);
        JsonObject response = new JsonObject(sourceAccountStatus);
        JsonObject sourceAccount = response.getJsonObject("value");
        BigDecimal sourceAccountAmount = new BigDecimal(sourceAccount.getString("amount"));

        Assert.assertEquals(BigDecimal.valueOf(5.30).setScale(2,BigDecimal.ROUND_HALF_UP), sourceAccountAmount);

        String targetAccountStatus = bank.getAccountStatus(targetIban);
        response = new JsonObject(targetAccountStatus);
        JsonObject targetAccount = response.getJsonObject("value");
        BigDecimal targetAccountAmount = new BigDecimal(targetAccount.getString("amount"));

        Assert.assertEquals(BigDecimal.valueOf(7.17).setScale(2, BigDecimal.ROUND_HALF_UP), targetAccountAmount);
    }

    @Test
    public void getAccountStatusTest() {
        String iban = getIbanFromResponse(bank.openAccount());
        bank.addMoneyToAccount(iban, BigDecimal.valueOf(24_700.89));

        HashMap<String, Object> map = new HashMap<>();
        map.put("iban", iban);

        given()
                .contentType("application/json")
                .body(map)
                .when()
                .post("http://localhost:4200/account/status")
                .then()
                .statusCode(200);

        String status = bank.getAccountStatus(iban);
        JsonObject response = new JsonObject(status);
        JsonObject account = response.getJsonObject("value");

        String ibanInStatus = account.getString("iban");
        assertThat(ibanInStatus, containsString("BANK_00000001"));

        BigDecimal amountInStatus = new BigDecimal(account.getString("amount"));
        Assert.assertEquals(BigDecimal.valueOf(24_700.89), amountInStatus);
    }

    @Test
    public void getAllAccountTest() {
        bank.openAccount();
        bank.openAccount();

        given().relaxedHTTPSValidation()
                .when()
                .get("http://localhost:4200/account/getAll")
                .then()
                .statusCode(200)
                .body("value.iban", hasItems("BANK_00000002"));
    }

    @Test
    public void deleteAllAccountsTest() {
        bank.openAccount();
        bank.openAccount();

        given().relaxedHTTPSValidation()
                .when()
                .delete("http://localhost:4200/account/deleteAll")
                .then()
                .statusCode(200);

        String response = bank.getAllAccounts();
        JsonObject json = new JsonObject(response);
        Integer accountsQty = json.getInteger("@odata.count");
        Assert.assertEquals(0, accountsQty.intValue());
    }
}
