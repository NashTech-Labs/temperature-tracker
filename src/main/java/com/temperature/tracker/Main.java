package com.temperature.tracker;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        /**
         * Clustered to use eventbus across the network.
         */
        Vertx.clusteredVertx(new VertxOptions())
                .onSuccess(vertx -> {
                    DeploymentOptions deploymentOptions = new DeploymentOptions()
                            .setWorker(true)
                            .setInstances(1);
                    vertx.deployVerticle("com.temperature.tracker.TemperatureTracker", deploymentOptions);
                })
                .onFailure(failure -> {
                    logger.error("Woops ", failure);
                });
    }
}
