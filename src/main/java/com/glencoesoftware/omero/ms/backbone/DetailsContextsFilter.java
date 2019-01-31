package com.glencoesoftware.omero.ms.backbone;

import org.slf4j.LoggerFactory;

import ome.model.internal.Details;
import ome.util.ContextFilter;
import ome.util.Filterable;

/**
 * Strips <code>Contexts</code> from {@link Details} instances in the object
 * graph so that the graph can be used with Java serialization.
 */
public class DetailsContextsFilter extends ContextFilter {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(DetailsContextsFilter.class);

    @Override
    public Filterable filter(String fieldId, Filterable f) {
        if (f instanceof Details) {
            log.debug("Removing contexts from fieldId:{} f:{}", fieldId, f);
            ((Details) f).setContexts(null);
        }
        return super.filter(fieldId, f);
    }

}
