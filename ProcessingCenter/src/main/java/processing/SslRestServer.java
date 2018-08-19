package processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;

class SslRestServer {
    private static final Logger logger = LoggerFactory.getLogger(SslRestServer.class);
    private static final int SSL_WEB_PORT = 4201;

    private final Processing processing;
    private final Vertx vertx;

    SslRestServer(Processing processing) {
        this.processing = processing;
        this.vertx = Vertx.vertx();
    }

    void start() {
        logger.info("REST Server is starting...");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
                .allowedMethod(io.vertx.core.http.HttpMethod.PATCH)
                .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowedHeader("Access-Control-Request-Method")
                .allowedHeader("Access-Control-Allow-Credentials")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Headers")
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        router.post("/account/deposit").handler(rc -> handlerWrapper(rc, this::addMoneyToAccount));
        router.post("/account/transfer").handler(rc -> handlerWrapper(rc, this::transferMoney));
        router.get("/stop").handler(routingContext -> handlerWrapper(routingContext, this::stopProcessing));

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions
                .setKeyCertOptions(new PemKeyCertOptions()
                        .setKeyPath("key.pem")
                        .setCertPath("cert.pem"))
                .setSsl(true);

        vertx.createHttpServer(httpServerOptions)
                .requestHandler(router::accept)
                .listen(SSL_WEB_PORT);
        logger.info("REST Server is ready on SSL port [{}]!", SSL_WEB_PORT);
    }

    private void addMoneyToAccount(RoutingContext rc) throws JsonProcessingException {
        JsonObject requestBody = rc.getBodyAsJson();
        String iban = requestBody.getString("iban");
        BigDecimal value = BigDecimal.valueOf(requestBody.getDouble("amount"));
        processing.addMoneyToAccount(iban, value);
        responseOk(null, rc);
    }

    private void transferMoney(RoutingContext rc) throws JsonProcessingException {
        JsonObject requestBody = rc.getBodyAsJson();
        String sourceIban = requestBody.getString("sourceIban");
        String targetIban = requestBody.getString("targetIban");
        BigDecimal value = BigDecimal.valueOf(requestBody.getDouble("amount"));
        processing.transferMoney(sourceIban,targetIban, value);
        responseOk(null, rc);
    }

    void stop() {
        vertx.close();
    }

    @SuppressWarnings("all")
    private void stopProcessing(RoutingContext rc) {
        processing.stop();
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
                .setStatusCode(200) // 200 OK
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(responseData);
    }
}