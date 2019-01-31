/*
 * Copyright (C) 2017 Glencoe Software, Inc. All rights reserved.
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

import org.testng.annotations.Test;

import org.testng.annotations.BeforeMethod;

import ome.model.containers.Dataset;
import ome.model.containers.Project;

/**
 * Tests that the details context filter performs the correct object graph
 * manipulations to ensure Java serialization is possible.
 */
public class DetailsContextFilterTest {

    private Project p;

    private Dataset d;

    @BeforeMethod
    public void setUp() {
        p = new Project(1L, true);
        p.getDetails().setContexts(
                new Object[] { new ome.system.PreferenceContext() });
        d = new Dataset(1L, true);
        d.getDetails().setContexts(
                new Object[] { new ome.system.PreferenceContext() });
        p.linkDataset(d);
    }

    @Test
    public void testNullContexts() throws Exception {
        new DetailsContextsFilter().filter("blarg", p);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(p);
        }
    }

}
