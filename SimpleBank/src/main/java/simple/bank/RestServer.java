package simple.bank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;

class RestServer {
    private static final Logger logger = LoggerFactory.getLogger(RestServer.class);
    private static final int WEB_PORT = 4200;

    private final Bank bank;
    private final Vertx vertx;

    RestServer(Bank bank) {
        this.bank = bank;
        this.vertx = Vertx.vertx();
    }

    void start() {
        logger.info("REST Server is starting...");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.put("/account/open").handler(rc -> handlerWrapper(rc, this::openAccount));
        router.post("/account/deposit").handler(rc -> handlerWrapper(rc, this::addMoneyToAccount));
        router.post("/account/transfer").handler(rc -> handlerWrapper(rc, this::transferMoney));
        router.post("/account/status").handler(rc -> handlerWrapper(rc, this::getAccountStatus));
        router.get("/account/getAll").handler(rc -> handlerWrapper(rc, this::getAllAccounts));
        router.delete("/account/deleteAll").handler(rc -> handlerWrapper(rc, this::deleteAllAccounts));
        router.get("/stop").handler(rc -> handlerWrapper(rc, this::stop));

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        vertx.createHttpServer(httpServerOptions)
                .requestHandler(router::accept)
                .listen(WEB_PORT);
        logger.info("REST Server is ready on port [{}]!", WEB_PORT);
    }

    void stop() {
        vertx.close();
    }

    private void openAccount(RoutingContext rc) throws JsonProcessingException {
        responseOk(bank.openAccount(), rc);
    }

    private void addMoneyToAccount(RoutingContext rc) throws JsonProcessingException {
        JsonObject requestBody = rc.getBodyAsJson();
        String iban = requestBody.getString("iban");
        BigDecimal value = BigDecimal.valueOf(requestBody.getDouble("amount"));
        bank.addMoneyToAccount(iban, value);
        responseOk(null, rc);
    }

    private void transferMoney(RoutingContext rc) throws JsonProcessingException {
        JsonObject requestBody = rc.getBodyAsJson();
        String sourceIban = requestBody.getString("sourceIban");
        String targetIban = requestBody.getString("targetIban");
        BigDecimal value = BigDecimal.valueOf(requestBody.getDouble("amount"));
        bank.transferMoney(sourceIban,targetIban, value);
        responseOk(null, rc);
    }

    private void getAccountStatus(RoutingContext rc) throws JsonProcessingException {
        JsonObject requestBody = rc.getBodyAsJson();
        String iban = requestBody.getString("iban");
        responseOk(bank.getAccountStatus(iban), rc);
    }

    private void getAllAccounts(RoutingContext rc) throws JsonProcessingException {
        responseOk(bank.getAllAccounts(), rc);
    }

    private void deleteAllAccounts(RoutingContext rc) throws JsonProcessingException {
        bank.deleteAllAccounts();
        responseOk(null, rc);
    }

    @SuppressWarnings("all")
    private void stop(RoutingContext rc) {
        bank.stop();
    }

    private void handlerWrapper(RoutingContext rc, CheckedConsumer<RoutingContext> handler) {
        try {
            handler.accept(rc);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            rc.fail(500);
        }
    }

    @FunctionalInterface
    interface CheckedConsumer<T> {
        void accept(T t) throws IOException;
    }

    private void responseOk(Object obj, RoutingContext rc) throws JsonProcessingException {
        String responseData;
        if (obj == null) {
            responseData = "{}";
        } else {
            responseData = obj instanceof String ? (String) obj : new ObjectMapper().writeValueAsString(obj);
        }
        rc.response()
                .setStatusCode(200)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(responseData);
    }
}
