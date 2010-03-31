/*
 * $Id: TocVegBaerPixelTest.java,v 1.3 2006/03/27 15:33:02 meris Exp $
 *
 * Copyright (C) 2002,2003  by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.esa.beam.processor.toc.utils;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TocVegBaerPixelTest extends TestCase {

    private TocVegBaerPixel _pixel;

    public TocVegBaerPixelTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(TocVegBaerPixelTest.class);
    }

    /**
     * Initializes the test environment.
     */
    protected void setUp() {
        _pixel = new TocVegBaerPixel();
        assertNotNull(_pixel);
    }

    /**
     * Tests the correct functionality of the default constructor.
     */
    public void testDefaultConstruction() {
        // all field shall be set to zero
        for (int n = 0; n < 11; n++) {
            assertEquals(0.f, _pixel.getBand(n), 1e-6);
        }
        assertEquals(0.f, _pixel.getBand_Lat(), 1e-6);
        assertEquals(0.f, _pixel.getBand_Lon(), 1e-6);
        assertEquals(0.f, _pixel.getBand_Sza(), 1e-6);
        assertEquals(0.f, _pixel.getBand_Saa(), 1e-6);
        assertEquals(0.f, _pixel.getBand_Vza(), 1e-6);
        assertEquals(0.f, _pixel.getBand_Vaa(), 1e-6);
        assertEquals(0.f, _pixel.getBand_TOAVEG(), 1e-6);
        assertEquals(0.f, _pixel.getBand_ALPHA(), 1e-6);
        assertEquals(0.f, _pixel.getBand_AOT_412(), 1e-6);
        assertEquals(0.f, _pixel.getBand_AOT_560(), 1e-6);
    }

    /**
     * Tests the correct functionality of the accessor methods.
     */
    public void testAcessors() {
        float fVal = 1.f;

        for (int n = 0; n < 11; n++) {
            _pixel.setBand(fVal, n);
            assertEquals(fVal, _pixel.getBand(n), 1e-6);
            ++fVal;
        }

        _pixel.setBand_Lat(fVal);
        assertEquals(fVal, _pixel.getBand_Lat(), 1e-6);
        ++fVal;

        _pixel.setBand_Lon(fVal);
        assertEquals(fVal, _pixel.getBand_Lon(), 1e-6);
        ++fVal;

        _pixel.setBand_Sza(fVal);
        assertEquals(fVal, _pixel.getBand_Sza(), 1e-6);
        ++fVal;

        _pixel.setBand_Saa(fVal);
        assertEquals(fVal, _pixel.getBand_Saa(), 1e-6);
        ++fVal;

        _pixel.setBand_Vza(fVal);
        assertEquals(fVal, _pixel.getBand_Vza(), 1e-6);
        ++fVal;

        _pixel.setBand_Vaa(fVal);
        assertEquals(fVal, _pixel.getBand_Vaa(), 1e-6);
        ++fVal;

        _pixel.setBand_TOAVEG(fVal);
        assertEquals(fVal, _pixel.getBand_TOAVEG(), 1e-6);
        ++fVal;

        _pixel.setBand_AOT_412(fVal);
        assertEquals(fVal, _pixel.getBand_AOT_412(), 1e-6);
        ++fVal;

        _pixel.setBand_AOT_560(fVal);
        assertEquals(fVal, _pixel.getBand_AOT_560(), 1e-6);
        ++fVal;

        _pixel.setBand_ALPHA(fVal);
        assertEquals(fVal, _pixel.getBand_ALPHA(), 1e-6);
        ++fVal;
    }

    /**
     * Tests that the reflectance accessors are behaving correctly when fed with out-of-range indices
     */
    public void testAccessorFailures() {
        try {
            _pixel.setBand(2.7f, -1);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
        }

        try {
            _pixel.setBand(0.2f, 11);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
        }

        try {
            _pixel.getBand(-3);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
        }

        try {
            _pixel.getBand(16);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
        }
    }
}
