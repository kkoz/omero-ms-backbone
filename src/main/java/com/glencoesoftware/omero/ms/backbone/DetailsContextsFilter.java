/*
 * Copyright (C) 2019 Glencoe Software, Inc. All rights reserved.
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

import ome.model.internal.Details;
import ome.util.ContextFilter;
import ome.util.Filterable;

/**
 * Strips <code>Contexts</code> from {@link Details} instances in the object
 * graph so that the graph can be used with Java serialization.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
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
