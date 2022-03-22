package com.temperature.dashboard;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackerDashboard extends AbstractVerticle {
    /**
     * create logger object for logging.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(TrackerDashboard.class);
    /**
     * gets the value of the specified
     * environment variable "HTTP_PORT" or default.
     */
    private static final int HTTP_PORT = Integer.parseInt(
            System.getenv().getOrDefault("HTTP_PORT", "8282"));
    /**
     * @param startPromise
     * @throws Exception
     */
    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
        final Router router = Router.router(vertx);
//         * using sockJsHandler to send the data on browser from eventBus.
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions()
                .addOutboundPermitted(new PermittedOptions()
                        .setAddress("temperature.updates"));
        sockJSHandler.bridge(bridgeOptions);
        router.route("/eventbus/*").handler(sockJSHandler);
        router.route().handler(StaticHandler.create("webroot"));
        router.get("/*").handler(ctx -> ctx.reroute("/index.html"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(ok -> {
                    LOGGER.info(
                            "Http server running : http://localhost:{}",
                            HTTP_PORT);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
    public static void main(final String[] args) {
//         * Clustered to use eventbus across the network.
        Vertx.clusteredVertx(new VertxOptions())
                .onSuccess(vertx1 -> {
                    vertx1.deployVerticle(new TrackerDashboard());
                    LOGGER.info("Running");
                })
                .onFailure(fail -> {
                    LOGGER.info("Failed");
                });
    }
}
