package com.temperature.tracker;
import com.temperature.tracker.dao.TemperatureTrackerDAO;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class TemperatureTracker extends AbstractVerticle {
    /**
     * create logger object for logging.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TemperatureTracker.class);
    /**
     * starting value of temperature that will be used.
     */
    private double temperature = 21.0;
    /**
     * Create random object to generate random values.
     */
    private final Random random = new Random();
    /**
     * create SimpleDateFormat object to format the date.
     */
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd,yyyy HH:mm");
    /**
     * config.json file path.
     */
    private static final String CONFIG_PATH = "src/main/resources/config.json";
    /**
     * create TemperatureTrackerDAO object.
     */
    private final TemperatureTrackerDAO temperatureTrackerDAO =
            new TemperatureTrackerDAO();
    /**
     *
     * @param startPromise
     * @throws Exception
     */
    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
//      To set the configuration file
        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject()
                        .put("path", CONFIG_PATH));
        ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
                .addStore(defaultConfig);
        ConfigRetriever configRetriever =
                ConfigRetriever.create(vertx, retrieverOptions);
//        consuming data from eventBus and
//        sending to recordTemperature() method to insert into database.
        vertx.eventBus()
                .consumer("temperature.updates", this::recordTemperature);
//        This handler will get called every second.
        vertx.setPeriodic(1000, this::updateTemperature);
//        Creating router object
        Router router = Router.router(vertx);
//        route requests
        router.route().handler(this::greetHello);
        router.get("/all").handler(this::allData);
        router.get("/last-give-minutes").handler(this::getLastFiveMinutes);
        router.get("/get/:id").handler(this::getDataById);
        Handler<AsyncResult<JsonObject>> handler = asyncResult ->
                this.handleConfigResult(asyncResult, router, startPromise);
        //  To retrieve the values from config.json file
        configRetriever.getConfig(handler);
    }
    private void greetHello(final RoutingContext context) {
        context.response().end("Welcome to Temperature Tracker API");
    }
    /**
     * This method will retrieve the values from config.json file
     * and create the server.
     * @param asyncResult
     * @param router
     * @param startPromise
     */
    private void handleConfigResult(final AsyncResult<JsonObject> asyncResult,
            final Router router, final Promise<Void> startPromise) {
        if (asyncResult.succeeded()) {
            JsonObject config = asyncResult.result();
            JsonObject http = config.getJsonObject("http");
            final int httpPort = http.getInteger("port");
            JsonObject db = config.getJsonObject("db");
            String dbUrl = db.getString("db_url");
            String dbName = db.getString("db_name");
//         ****************** Mongodb connection ****************
            temperatureTrackerDAO.createConnection(vertx, dbUrl, dbName);
//            creating server
            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(httpPort)
                    .onSuccess(ok -> {
                        LOGGER.info(
                                "http server is running: http://127.0.0.1:{}",
                                httpPort);
                        startPromise.complete();
                    })
                    .onFailure(startPromise::fail);
        } else {
            LOGGER.info(asyncResult.cause().getMessage());
        }
    }
    /**
     * Getting last five minutes data.
     * @param context
     */
    private void getLastFiveMinutes(final RoutingContext context) {
        LOGGER.info("Processing http request from {} ",
                context.request().remoteAddress());
        JsonArray data = new JsonArray();
        Handler<AsyncResult<List<JsonObject>>> handler = res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    Long milliSeconds = json.getLong("timestamp");
                    //creating Date from millisecond
                    Date date = new Date(milliSeconds);
                    data.add(new JsonObject()
                            .put("id", json.getString("_id"))
                            .put("temperature",
                                    json.getDouble("temperature"))
                            .put("DateTime", dateFormat.format(date))
                    );
                }
            } else {
                res.cause().printStackTrace();
            }
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(data.encode())
                    .onFailure(error -> {
                        context.fail(
                                HttpResponseStatus
                                        .INTERNAL_SERVER_ERROR.code());
                        LOGGER.info("Woops", error);
                    });
        };
        temperatureTrackerDAO.getLastFiveMints(handler);
    }
    /**
     * Inserting data into database.
     * @param jsonObjectMessage
     */
    private void recordTemperature(
            final Message<JsonObject> jsonObjectMessage) {
        long timestamp = System.currentTimeMillis();
        JsonObject jsonObject = new JsonObject()
                .put("temperature", temperature)
                .put("timestamp", timestamp);
        Handler<AsyncResult<String>> resultHandler = handler -> {
            if (handler.succeeded()) {
                LOGGER.info("Recorded " + handler.result());
            } else {
                LOGGER.error("Recording failed ");
                handler.cause().printStackTrace();
            }
        };
        temperatureTrackerDAO.recordTemperature(jsonObject, resultHandler);
    }
    /**
     * Getting all the data.
     * @param context
     */
    private void allData(final RoutingContext context) {
        LOGGER.info("Processing all data from {} ",
                context.request().remoteAddress());
        JsonArray data = new JsonArray();
        Handler<AsyncResult<List<JsonObject>>> handler =  res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    Long milliSeconds = json.getLong("timestamp");
                    //creating Date from millisecond
                    Date date = new Date(milliSeconds);
                    data.add(new JsonObject()
                            .put("id", json.getString("_id"))
                            .put("temperature",
                                    json.getDouble("temperature"))
                            .put("DateTime", dateFormat.format(date))
                    );
                }
            } else {
                LOGGER.info("Woops");
                context.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            }
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("data", data).encode());
        };
        temperatureTrackerDAO.getAllData(handler);
    }
    /**
     * Getting data by id.
     * @param context
     */
        private void getDataById(final RoutingContext context) {
        LOGGER.info("Processing http request from {} ",
                context.request().remoteAddress());
        JsonArray data = new JsonArray();
        Handler<AsyncResult<List<JsonObject>>> resultHandler = res -> {
            if (res.succeeded()) {
                for (JsonObject json : res.result()) {
                    Long milliSeconds = json.getLong("timestamp");
                    //creating Date from millisecond
                    Date date = new Date(milliSeconds);
                    data.add(new JsonObject()
                            .put("id", json.getString("_id"))
                            .put("temperature",
                                        json.getDouble("temperature"))
                            .put("DateTime", dateFormat.format(date)));
                }
            } else {
                res.cause().printStackTrace();
            }
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(data.encode())
                    .onFailure(error -> {
                        context.fail(HttpResponseStatus
                                .INTERNAL_SERVER_ERROR.code());
                        LOGGER.info("Woops", error);
                    });
        };
        temperatureTrackerDAO.getDataById(context, resultHandler);
    }
    /**
     *  creating payload.
     * @return json object.
     */
    private JsonObject createPayload() {
        return new JsonObject()
                .put("temperature", temperature)
                .put("timestamp", System.currentTimeMillis());
    }
    /**
     * updating temperature and publish to eventBus.
     * @param aLong
     */
    private void updateTemperature(final Long aLong) {
        temperature = temperature + (random.nextGaussian() / 2.0d);
        LOGGER.info("Temperature Update: {}", temperature);
        vertx.eventBus().publish("temperature.updates", createPayload());
    }
}
