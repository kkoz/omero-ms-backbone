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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import ome.system.PreferenceContext;


/**
 * Main entry point for the OMERO microservice architecture backbone verticle.
 * <b>NOTE:</b> As this verticle is being instantiated by Spring 3 inside the
 * OMERO server it <b>CANNOT</b> contain any Java 8+ lambda expressions in
 * directly accessible code paths.  If you are seeing
 * {@link ArrayIndexOutOfBoundsException}'s thrown from Spring ASM during bean
 * instantiation, check for lambda expressions.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
 */
public class BackboneService {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneService.class);

    public final static int DEFAULT_WORKER_POOL_SIZE =
            Runtime.getRuntime().availableProcessors() * 2;

    BackboneService(PreferenceContext preferenceContext,
                    VerticleFactory verticleFactory) {
        String clusterHost = Optional.ofNullable(preferenceContext.getProperty(
            "omero.ms.backbone.cluster_host"
        )).orElse(VertxOptions.DEFAULT_CLUSTER_HOST);
        int workerPoolSize = Integer.parseInt(Optional.ofNullable(
            preferenceContext.getProperty("omero.ms.backbone.worker_pool_size")
        ).orElse(Integer.toString(DEFAULT_WORKER_POOL_SIZE)));

        log.debug(
            "Initializing Backbone -- cluster host {} worker pool size {}",
            clusterHost, workerPoolSize
        );
        DeploymentOptions verticleOptions = new DeploymentOptions()
                .setWorker(true)
                .setInstances(workerPoolSize)
                .setWorkerPoolSize(workerPoolSize);

        Config hazelcastConfig = getHazelcastConfig();
        hazelcastConfig.setProperty("hazelcast.logging.type", "slf4j");
        ClusterManager clusterManager =
                new HazelcastClusterManager(hazelcastConfig);
        VertxOptions options = new VertxOptions()
                .setClusterManager(clusterManager)
                .setClusterHost(clusterHost);
        Vertx.clusteredVertx(options, new Handler<AsyncResult<Vertx>>() {
            @Override
            public void handle(AsyncResult<Vertx> event) {
                if (event.succeeded()) {
                    Vertx vertx = event.result();
                    vertx.registerVerticleFactory(verticleFactory);
                    vertx.deployVerticle(
                            "omero:omero-ms-backbone-verticle",
                            verticleOptions, res -> {
                        if (res.failed()) {
                            log.error(
                                "Failure deploying verticle", res.cause());
                        } else {
                            log.debug("Succeeded in deploying verticle");
                        }
                    });
                } else {
                    log.error("Failed to start Hazelcast clustered Vert.x");
                }
            }
        });
    }

    /**
     * Retrieves the Hazelcast configuration either from the OMERO
     * configuration directory or Hazelcast defaults.
     */
    private Config getHazelcastConfig() {
        File configFile =
                new File(new File(new File("."), "etc"), "hazelcast.xml");
        Config config = new Config();
        if (configFile.exists()) {
            log.info("Loading Hazelcast configuration: {}",
                     configFile.getAbsolutePath());
            try (InputStream is = new FileInputStream(configFile);
                 InputStream bis = new BufferedInputStream(is)) {
                config = new XmlConfigBuilder(bis).build();
            } catch (IOException e) {
                log.error("Failed to read Hazelcast configuration", e);
            }
        } else {
            log.debug("Hazelcast configuration file {} not found",
                      configFile.getAbsolutePath());
        }
        return config;
    }
}
