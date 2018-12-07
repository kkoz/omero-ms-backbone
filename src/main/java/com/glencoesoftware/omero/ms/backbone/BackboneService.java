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

import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import ome.services.sessions.SessionManager;
import ome.services.util.Executor;


/**
 * Main entry point for the OMERO microservice architecture backbone verticle.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
 */
public class BackboneService {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneService.class);

    private final BackboneVerticle backboneVerticle;

    private final Executor executor;

    private final SessionManager sessionManager;

    BackboneService(Executor executor, SessionManager sessionManager) {
        this.executor = executor;
        this.sessionManager = sessionManager;

        log.debug("Initializing Backbone Service");
        backboneVerticle = new BackboneVerticle(executor, sessionManager);

        ClusterManager clusterManager = new HazelcastClusterManager();
        VertxOptions options =
                new VertxOptions().setClusterManager(clusterManager);
        Vertx.clusteredVertx(options, new Handler<AsyncResult<Vertx>>() {
            @Override
            public void handle(AsyncResult<Vertx> event) {
                if (event.succeeded()) {
                    Vertx vertx = event.result();
                    vertx.deployVerticle(backboneVerticle);
                } else {
                    log.error("Failed to start Hazelcast clustered Vert.x");
                }
            }
        });
    }

}
