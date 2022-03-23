package com.temperature.tracker.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import java.util.List;

public class TemperatureTrackerDAO {
    /**
     * create mongodb client to communicate with mongodb.
     */
    private MongoClient client;
    /**
     * creating the database connection.
     * @param vertx
     * @param dbUrl
     * @param dbName
     * @return MongoClient object.
     */
    public MongoClient createConnection(
            final Vertx vertx, final String dbUrl,
            final String dbName) {
        client = MongoClient.create(vertx, new JsonObject()
                .put("url", dbUrl)
                .put("db_name", dbName));
        return client;
    }
    /**
     * Inserting data into database.
     * @param jsonObject
     * @param resultHandler
     */
    public void recordTemperature(
            final JsonObject jsonObject,
            final Handler<AsyncResult<String>> resultHandler) {
        client.insert("temperature_records", jsonObject, resultHandler);
    }
    /**
     * Getting last five minutes data.
     * @param resultHandler
     * @return MongoClient object with data.
     */
    public MongoClient getLastFiveMints(
            final Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        final Long lastFive = System.currentTimeMillis() - (5 * 60 * 1000);
        JsonObject lastFiveMinutesQuery = new JsonObject()
                .put("timestamp", new JsonObject().put("$gte", lastFive));
        client.find("temperature_records", lastFiveMinutesQuery, resultHandler);
        return client;
    }
    /**
     * get all the data from database.
     * @param resultHandler
     * @return MongoClient object with data.
     */
    public MongoClient getAllData(
            final Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        JsonObject query = new JsonObject()
                .put("timestamp", new JsonObject().put("$gt", 1000));
        MongoClient mongoClient = client.find(
                "temperature_records", query, resultHandler);
        return mongoClient;
    }
    /**
     * Get data by id.
     * @param context
     * @param resultHandler
     * @return MongoClient object with data.
     */
    public MongoClient getDataById(
            final RoutingContext context,
            final Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        String id = context.request().getParam("id");
        JsonObject findQuery = new JsonObject()
                .put("_id", id);
        client.find("temperature_records", findQuery, resultHandler);
        return client;
    }
}
