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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.hibernate.Session;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import ome.api.IPixels;
import ome.api.IQuery;
import ome.conditions.RemovedSessionException;
import ome.conditions.SessionTimeoutException;
import ome.model.IObject;
import ome.model.core.Image;
import ome.model.core.Pixels;
import ome.model.display.RenderingDef;
import ome.parameters.Parameters;
import ome.services.sessions.SessionManager;
import ome.services.util.Executor;
import ome.system.Principal;
import ome.system.ServiceFactory;
import omero.util.IceMapper;


/**
 * Main entry point for the OMERO microservice architecture backbone verticle.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
 */
public class BackboneVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneVerticle.class);

    public static final String IS_SESSION_VALID_EVENT =
            "omero.is_session_valid";

    public static final String CAN_READ_EVENT =
            "omero.can_read";

    public static final String GET_OBJECT_EVENT =
            "omero.get_object";

    public static final String GET_ALL_ENUMERATIONS_EVENT =
            "omero.get_all_enumerations";

    public static final String GET_RENDERING_SETTINGS_EVENT =
            "omero.get_rendering_settings";

    public static final String GET_PIXELS_DESCRIPTION_EVENT =
            "omero.get_pixels_description";

    /** OMERO server Spring application context. */
    private ApplicationContext context;

    private final Executor executor;

    private final SessionManager sessionManager;

    private final DetailsContextsFilter contextsFilter =
            new DetailsContextsFilter();

    BackboneVerticle(Executor executor, SessionManager sessionManager) {
        this.executor = executor;
        this.sessionManager = sessionManager;
    }

    /**
     * Entry point method which starts the server event loop.
     * @param args Command line arguments.
     */
    @Override
    public void start(Future<Void> future) {
        log.info("Starting verticle");

        vertx.eventBus().<String>consumer(
                IS_SESSION_VALID_EVENT, this::isSessionValid);
        vertx.eventBus().<JsonObject>consumer(
                CAN_READ_EVENT, this::canRead);
        vertx.eventBus().<JsonObject>consumer(
                GET_OBJECT_EVENT, this::getObject);
        vertx.eventBus().<JsonObject>consumer(
                GET_ALL_ENUMERATIONS_EVENT, this::getAllEnumerations);
        vertx.eventBus().<JsonObject>consumer(
                GET_RENDERING_SETTINGS_EVENT, this::getRenderingSettings);
        vertx.eventBus().<JsonObject>consumer(
                GET_PIXELS_DESCRIPTION_EVENT, this::getPixelsDescription);
    }

    private void handleMessageWithJob(BackboneSimpleWork job) {
        Message<JsonObject> message = job.getMessage();
        JsonObject data = message.body();
        String sessionKey = data.getString("sessionKey");
        try {
            ome.model.meta.Session session = null;
            try {
                 session = (ome.model.meta.Session)
                        sessionManager.find(sessionKey);
            } catch (RemovedSessionException | SessionTimeoutException e) {
                // No-op
            }
            if (session == null) {
                message.fail(403, "Session invalid");
                return;
            }
            Principal principal = new Principal(
                    session.getUuid(),
                    "-1",
                    session.getDefaultEventType());
            Object o = executor.execute(principal, job);
            // May contain non-serializable objects
            contextsFilter.filter("", o);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(o);
            }
            message.reply(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failure encoding", e);
            message.fail(500, e.getMessage());
        }
    }

    private void isSessionValid(Message<String> message) {
        String sessionKey =
                new JsonObject(message.body()).getString("sessionKey");
        try {
            message.reply(new Boolean(sessionManager.find(sessionKey) != null));
        } catch (Exception e) {
            message.reply(Boolean.FALSE);
        }
    }

    private void canRead(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "canRead") {
            @Transactional(readOnly = true)
            public Boolean doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    message.reply(new Boolean(
                            iQuery.find(klass, data.getLong("id")) != null));
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getObject(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getObject") {
            @Transactional(readOnly = true)
            public IObject doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    return iQuery.get(klass, data.getLong("id"));
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getAllEnumerations(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getAllEnumerations") {
            @Transactional(readOnly = true)
            public List<? extends IObject> doWork(Session session, ServiceFactory sf) {
                try {
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    return iPixels.getAllEnumerations(klass);
                } catch (Exception e) {
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getRenderingSettings(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getRenderingSettings") {
            @Transactional(readOnly = true)
            public RenderingDef doWork(Session session, ServiceFactory sf) {
                try {
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Long pixelsId = data.getLong("pixelsId");
                    return iPixels.retrieveRndSettings(pixelsId);
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getPixelsDescription(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getPixelsDescription") {
            @Transactional(readOnly = true)
            public Pixels doWork(Session session, ServiceFactory sf) {
                try {
                    IQuery iQuery = sf.getQueryService();
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Parameters parameters = new Parameters();
                    parameters.addId(data.getLong("imageId"));
                    Image image = iQuery.findByQuery(
                            "SELECT i FROM Image as i " +
                            "JOIN FETCH i.pixels " +
                            "WHERE i.id = :id",
                            parameters);
                    Pixels pixels = iPixels.retrievePixDescription(
                            image.getPrimaryPixels().getId());
                    pixels.setImage(image);
                    return pixels;
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

}
