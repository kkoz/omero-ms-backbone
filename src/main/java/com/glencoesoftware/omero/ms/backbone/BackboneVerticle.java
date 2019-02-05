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
import ome.model.IObject;
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

    public static final String GET_OBJECT_EVENT =
            "omero.get_object";

    public static final String GET_ALL_ENUMERATIONS_EVENT =
            "omero.get_all_enumerations";

    public static final String GET_PIXELS_ID_AND_SERIES =
            "omero.get_pixels_id_and_series";

    public static final String GET_RENDERING_SETTINGS =
            "omero.get_rendering_settings";

    public static final String GET_PIXELS =
            "omero.get_pixels";

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
                GET_OBJECT_EVENT, this::getObject);
        vertx.eventBus().<String>consumer(
                GET_ALL_ENUMERATIONS_EVENT, this::getAllEnumerations);
        vertx.eventBus().<String>consumer(
                GET_PIXELS_ID_AND_SERIES, this::getPixelsIdAndSeries);
        vertx.eventBus().<String>consumer(
                GET_RENDERING_SETTINGS, this::getRenderingSettings);
        vertx.eventBus().<String>consumer(
                GET_PIXELS, this::getPixels);
    }

    private ome.model.meta.Session getSession(JsonObject data) {
        String sessionKey = data.getString("sessionKey");
        log.debug("Session key: " + sessionKey);

        return (ome.model.meta.Session) sessionManager.find(sessionKey);
    }
    
    private void handleMessageWithJob(Message<String> message,
    		Executor.SimpleWork job) {
        JsonObject data = new JsonObject(message.body());
        String sessionKey = data.getString("sessionKey");
        log.debug("Session key: " + sessionKey);

        try {
            ome.model.meta.Session session = getSession(data);
            if (session == null) {
                message.fail(403, "Session invalid");
                return;
            }
            Principal principal = new Principal(
                    session.getUuid(),
                    "-1",
                    session.getDefaultEventType());
            Object o = executor.execute(
                    principal, job);
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

    private void getObject(Message<String> message) {
        JsonObject data = new JsonObject(message.body());
        
        Executor.SimpleWork job = new Executor.SimpleWork(this, "getObject") {
            @Transactional(readOnly = true)
            public IObject doWork(Session session, ServiceFactory sf) {
                IQuery iQuery = sf.getQueryService();
                try {
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
        handleMessageWithJob(message, job);
    }

    private void getAllEnumerations(Message<String> message) {
    	JsonObject data = new JsonObject(message.body());
    	Executor.SimpleWork job = new Executor.SimpleWork(this, "test") {
            @Transactional(readOnly = true)
            public List<? extends IObject> doWork(Session session, ServiceFactory sf) {
                IPixels iPixels = sf.getPixelsService();
                try {
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
        handleMessageWithJob(message, job);
    }

    private void getPixelsIdAndSeries(Message<String> message) {
        JsonObject data = new JsonObject(message.body());
        Executor.SimpleWork job = new Executor.SimpleWork(this, "getPixelsIdAndSeries") {
            @Transactional(readOnly = true)
            public List<Object[]> doWork(Session session, ServiceFactory sf) {
                IQuery iQuery = sf.getQueryService();
                Parameters parameters = new Parameters();
                parameters.addId(data.getLong("imageId"));
                try {
                    return iQuery.projection(
                        "SELECT p.id, p.image.series FROM Pixels as p " +
                        "WHERE p.image.id = :id", parameters);
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(message, job);
    }

    private void getRenderingSettings(Message<String> message) {
        JsonObject data = new JsonObject(message.body());
        Executor.SimpleWork job = new Executor.SimpleWork(this, "getRenderingSettings") {
            @Transactional(readOnly = true)
            public RenderingDef doWork(Session session, ServiceFactory sf) {
                IPixels iPixels = sf.getPixelsService();
                try {
                    Long pixelsId = data.getLong("pixelsId");
                    return iPixels.retrieveRndSettings(pixelsId);
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(message, job);
    }

    private void getPixels(Message<String> message) {
        JsonObject data = new JsonObject(message.body());
        Executor.SimpleWork job = new Executor.SimpleWork(this, "getPixels") {
            @Transactional(readOnly = true)
            public List<Object[]> doWork(Session session, ServiceFactory sf) {
                IQuery iQuery = sf.getQueryService();
                Parameters parameters = new Parameters();
                parameters.addId(data.getLong("imageId"));
                try {
                    return iQuery.projection("SELECT p FROM Pixels as p " +
                            "JOIN FETCH p.image " +
                            "JOIN FETCH p.pixelsType " +
                            "WHERE p.image.id = :id",
                            parameters);
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(message, job);
    }

}
