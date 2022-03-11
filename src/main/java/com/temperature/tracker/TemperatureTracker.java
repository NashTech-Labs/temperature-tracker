package com.temperature.tracker;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class TemperatureTracker extends AbstractVerticle {
    /**
     * create logger object for logging.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TemperatureTracker.class);
    /**
     * gets the value of the specified
     * environment variable "HTTP_PORT" or default.
     */
    private static final int HTTP_PORT = Integer.parseInt(
            System.getenv().getOrDefault("HTTP_PORT", "8181"));
    /**
     * Generating random uuid.
     */
    private final String uuid = UUID.randomUUID().toString();
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
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd,yyyy HH:mm");
    /**
     * Create the pool.
     */
    private PgPool pgPool;
    /**
     *
     * @param startPromise
     * @throws Exception
     */
    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
//        creating database connection.
        pgPool = PgPool.pool(vertx, new PgConnectOptions()
                .setPort(5432)
                .setUser("aasif")
                .setDatabase("temperaturedb")
                .setPassword("aasif@12"), new PoolOptions());
//        consuming data from eventBus and
//        sending to recordTemperature() method to insert into database.
        vertx.eventBus()
                .consumer("temperature.updates", this::recordTemperature);
        // This handler will get called every second
        vertx.setPeriodic(1000, this::updateTemperature);
        // Creating router object
        Router router = Router.router(vertx);
        router.get("/all").handler(this::allData);
        router.get("/last-give-minutes").handler(this::getLastFiveMinutes);
        router.get("/get/:uuid").handler(this::getDataByUuid);
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(ok -> {
                    LOGGER.info(
                            "http server is running: http://127.0.0.1:{}",
                            HTTP_PORT);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
    /**
     * Getting last five minutes data.
     * @param context
     */
    private void getLastFiveMinutes(final RoutingContext context) {
        LOGGER.info("Processing http request from {} ",
                context.request().remoteAddress());
        Long lastFive = System.currentTimeMillis() - (5 * 60 * 1000);
        String query =
                "select * from temperature_records where tstamp >= '"
                        + lastFive + "'";
        JsonArray data = new JsonArray();
        pgPool.preparedQuery(query)
                .execute(ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> rows = ar.result();
                        for (Row row: rows) {
                            Long milliSeconds = row.getLong("tstamp");
                            //creating Date from millisecond
                            Date date = new Date(milliSeconds);
                            data.add(new JsonObject()
                                    .put("uuid", row.getString("uuid"))
                                    .put("temperature", row.getDouble("value"))
                                    .put("DateTime", sdf.format(date))
                            );
                        }
                        context.response()
                                .putHeader("Content-Type", "application/json")
                                .end(data.encode())
                                .onFailure(error -> {
                                    context.fail(500);
                                    LOGGER.info("Woops", error);
                                });
                    }
                });
    }
    /**
     * Inserting data into database.
     * @param jsonObjectMessage
     */
    private void recordTemperature(
            final Message<JsonObject> jsonObjectMessage) {
         JsonObject body = jsonObjectMessage.body();
        long timestamp = System.currentTimeMillis();
        String query =
                "insert into temperature_records("
                        + "uuid, value, tstamp) values ($1,$2,$3)";
        String uuid = body.getString("uuid");
        double temperature = body.getDouble("temperature");
        Tuple tuple = Tuple.of(uuid + timestamp, temperature, timestamp);
        pgPool.preparedQuery(query)
                .execute(tuple)
                .onSuccess(rows ->
                        LOGGER.info("Recorded " + tuple.deepToString()))
                .onFailure(failure ->
                        LOGGER.error("Recording failed " + failure));
    }
    /**
     * Getting all the data.
     * @param context
     */
    private void allData(final RoutingContext context) {
        LOGGER.info("Processing all data from {} ",
                context.request().remoteAddress());
        String query = "select * from temperature_records";
        pgPool.preparedQuery(query)
                .execute()
                .onSuccess(rows -> {
                    JsonArray jsonArray = new JsonArray();
                    for (Row row:rows) {
                        Long milliSeconds = row.getLong("tstamp");
                        //creating Date from millisecond
                        Date date = new Date(milliSeconds);
                        jsonArray.add(new JsonObject()
                                .put("uuid", row.getString("uuid"))
                                .put("temp", Math.
                                        round(row.
                                                getDouble("value")
                                                * 100.0) / 100.0)
                                .put("timestamp", sdf.format(date)));
                    }
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("data", jsonArray).encode());
                })
                .onFailure(failure -> {
                    LOGGER.info("Woops", failure);
                    context.fail(500);
                });
    }
    /**
     * Getting data by uuid.
     * @param context
     */
        private void getDataByUuid(final RoutingContext context) {
        LOGGER.info("Processing http request from {} ",
                context.request().remoteAddress());
        String query = "select * from temperature_records where uuid = $1";
        String uuid = context.request().getParam("uuid");
//        JsonObject payload = createPayload();
        JsonArray data = new JsonArray();
        pgPool.preparedQuery(query)
                .execute(Tuple.of(uuid), ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> rows = ar.result();
                        for (Row row: rows) {
                            Long milliSeconds = row.getLong("tstamp");
                            //creating Date from millisecond
                            Date date = new Date(milliSeconds);
                            System.out.println(row.getDouble("value"));
                            data.add(new JsonObject()
                                    .put("uuid", row.getString("uuid"))
                                    .put("temperature", row.getDouble("value"))
                                    .put("timestamp", sdf.format(date))
                            );
                        }
                        context.response()
                                .putHeader("Content-Type", "application/json")
                                .end(data.encode())
                                .onFailure(error -> {
                                    context.fail(500);
                                    LOGGER.info("Woops", error);
                                });
                    }
                });
    }
    /**
     *  creating payload.
     * @return json object.
     */
    private JsonObject createPayload() {
        return new JsonObject()
                .put("uuid", uuid)
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
