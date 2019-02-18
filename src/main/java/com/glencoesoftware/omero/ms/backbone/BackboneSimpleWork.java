package com.glencoesoftware.omero.ms.backbone;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import ome.services.util.Executor;

public abstract class BackboneSimpleWork extends Executor.SimpleWork {

    private Message<JsonObject> message;

    public BackboneSimpleWork(
            Message<JsonObject> message, Object o, String method, Object...params) {
        super(o, method, params);
        this.message = message;
    }

    public Message<JsonObject> getMessage() {
        return message;
    }

}
