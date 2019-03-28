/*
 * Copyright (C) 2018 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.omero.ms.backbone;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;
import java.lang.Integer;

import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import ome.model.annotations.FileAnnotation;
import ome.model.core.Image;
import ome.model.core.Pixels;


/**
 * Main entry point for the OMERO microservice architecture backbone verticle.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
 */
public class QueryVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(QueryVerticle.class);

    /**
     * Entry point method which starts the server event loop.
     * @param args Command line arguments.
     */
    @Override
    public void start(Future<Void> future) {
        log.info("Starting verticle");
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Thumbnail request handlers
        router.get("/api/:sessionKey/isSessionValid")
            .handler(this::isSessionValid);

        router.get("/api/:sessionKey/canRead/:type/:id")
            .handler(this::canRead);

        router.get("/api/:sessionKey/get/:type/:id")
            .handler(this::get);

        router.get("/api/:sessionKey/getAllEnumerations/:type")
            .handler(this::getAllEnumerations);

        router.get("/api/:sessionKey/getRenderingSettings/:pixelsId")
            .handler(this::getRenderingSettings);

        router.get("/api/:sessionKey/getPixelsDescription/:imageId")
            .handler(this::getPixels);

        router.get("/api/:sessionKey/getFileAnnotation/:annotationId")
        .handler(this::getFileAnnotation);

        router.get("/api/:sessionKey/getFilePath/:annotationId")
        .handler(this::getFilePath);

        router.get("/api/:sessionKey/getFilePathOriginalFile/:fileId")
        .handler(this::getFilePathOriginalFile);

        int port = 9090;  // FIXME
        log.info("Starting HTTP server *:{}", port);
        server.requestHandler(router::accept).listen(port, result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
    }

    private <T> void ifFailed(
            HttpServerResponse response, AsyncResult<Message<T>> result) {
        Throwable t = result.cause();
        int statusCode = 404;
        if (t instanceof ReplyException) {
            statusCode = ((ReplyException) t).failureCode();
        }
        log.error("Request failed", t);
        response.setStatusCode(statusCode);
    }

    private <T> T deserialize(AsyncResult<Message<byte[]>> result)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais =
                new ByteArrayInputStream(result.result().body());
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }

    private void isSessionValid(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        vertx.eventBus().<Boolean>send(
                BackboneVerticle.IS_SESSION_VALID_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = result.result().body().toString();
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void canRead(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        String type = request.params().get("type");
        long id = Long.parseLong(request.params().get("id"));
        log.debug("Type: {} Id: {}", type, id);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("type", type);
        data.put("id", id);
        vertx.eventBus().<Boolean>send(
                BackboneVerticle.CAN_READ_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = result.result().body().toString();
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void get(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        String type = request.params().get("type");
        long id = Long.parseLong(request.params().get("id"));
        log.debug("Type: {} Id: {}", type, id);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("type", type);
        data.put("id", id);
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_OBJECT_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = deserialize(result).toString();
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void getAllEnumerations(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        String type = request.params().get("type");
        log.debug("Type: {}", type);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("type", type);
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_ALL_ENUMERATIONS_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = deserialize(result).toString();
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void getRenderingSettings(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        Long pixelsId = Long.parseLong(request.params().get("pixelsId"));
        log.debug("Pixels ID: {}", pixelsId);

        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("sessionKey", sessionKey);
        data.put("pixelsId", pixelsId);
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_RENDERING_SETTINGS_EVENT,
                Json.encode(data), result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = deserialize(result).toString();
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void getPixels(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        Long imageId = Long.parseLong(request.params().get("imageId"));
        log.debug("Image ID: {}", imageId);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("imageId", imageId);
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_PIXELS_DESCRIPTION_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                Pixels pixels = deserialize(result);
                Image image = pixels.getImage();
                s = String.format(
                        "%s;%s;Series:%s", pixels, image, image.getSeries());
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void getFileAnnotation(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        Long annotationId = Long.parseLong(request.params().get("annotationId"));
        log.debug("Image ID: {}", annotationId);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("annotationId", annotationId);
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_FILE_ANNOTATION_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                FileAnnotation fileAnnotation = deserialize(result);
                s = String.format(
                        "%s;%s;", fileAnnotation, fileAnnotation.getFile());
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
                log.debug("Response ended");
            }
        });
    }

    private void getFilePath(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        Long annotationId = Long.parseLong(request.params().get("annotationId"));
        log.debug("Image ID: {}", annotationId);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("id", annotationId);
        data.put("type", "FileAnnotation");
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_FILE_PATH_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = deserialize(result);
                log.debug("Response ended");
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
            }
        });
    }

    private void getFilePathOriginalFile(RoutingContext event) {
        final HttpServerRequest request = event.request();
        final HttpServerResponse response = event.response();
        String sessionKey = request.params().get("sessionKey");
        log.debug("Session key: " + sessionKey);
        Long fileId = Long.parseLong(request.params().get("fileId"));
        log.debug("Image ID: {}", fileId);

        final JsonObject data = new JsonObject();
        data.put("sessionKey", sessionKey);
        data.put("id", fileId);
        data.put("type", "OriginalFile");
        vertx.eventBus().<byte[]>send(
                BackboneVerticle.GET_FILE_PATH_EVENT, data, result -> {
            String s = "";
            try {
                if (result.failed()) {
                    ifFailed(response, result);
                    return;
                }
                response.headers().set("Content-Type", "text/plain");
                s = deserialize(result);
                log.debug("Response ended");
            } catch (IOException | ClassNotFoundException e) {
                log.error("Exception while decoding object in response", e);
            } finally {
                response.end(s);
            }
        });
    }

}
