/*
 * $Id: SmacCoefficientsManager.java,v 1.2 2005/04/06 12:09:37 meris Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.processor.baer.auxdata;


import org.esa.beam.util.Guardian;
import org.esa.beam.util.ObjectUtils;
import org.esa.beam.util.io.CsvReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;

/**
 * Manages the mapping between the satellite sensor bands and the appropriate SMAC coefficient sets.
 * <p/>
 * This mapping is configurable from the outside. The class <code>SmacCoefficientsManager</code>
 * expects an URL pointing to a directoy where the mapping file "SensorMap.txt" and the
 * appropriate coefficient files are located.
 * <p/>
 * The syntax for the map file is very simple, it is a separated ascii (separation character is '|') table
 * with the following entries:
 * <p/>
 * SENSOR_NAME | BAND_NAME | ATMOSPHERE_TYPE | COEFFICIENT_FILE
 * <p/>
 * SENSOR_NAME - is defined by the public fields xxx_NAME where xxx denotes the satellite sensor
 * BAND_NAME - must match the band names defined in <code>EnvisatConstants</code>
 * ATMOSPHERE_TYPE - dependent on the coefficient set - this string will be seen in the SMAC UI
 * COEFFICIENT_FILE - name of the coefficient file
 */

public class SmacCoefficientsManager {

    public static final String AER_DES_NAME = "DES";
    public static final String AER_CONT_NAME = "CONT";
    public static final String MERIS_NAME = "MERIS";
    public static final String AATSR_NAME = "AATSR";

    private static final char[] _fieldSeparators = {'|'};
    private static final String _mapFileName = "SensorMap.txt";

    private CsvReader _csvReader;
    private Vector<SensorDb> _sensors;
    private File _location;

    /**
     * Constructs the object with default parameters.
     */
    public SmacCoefficientsManager() {
        init();
    }

    /**
     * Constructs the object with a given location for the sensor coefficient files.
     * Scans this URL for valid files.
     *
     * @param location the URL where the sensor coefficient files reside
     *
     * @throws java.io.IOException when unable to access <code>URL</code>
     */
    public SmacCoefficientsManager(File location) throws IOException {
        Guardian.assertNotNull("location", location);

        init();
        loadSensorCoefficients(location);
    }

    /**
     * Scans the directory for valid files.
     *
     * @param location the directory where the coefficients reside
     *
     * @throws java.io.IOException
     */
    public void loadSensorCoefficients(File location) throws IOException {
        Guardian.assertNotNull("location", location);
        File mapFile = new File(location, _mapFileName);

        _location = location;

        final InputStream in = new FileInputStream(mapFile);
        try {
            _csvReader = new CsvReader(new InputStreamReader(in), _fieldSeparators);

            // read sensor map file completely and assemble the database
            // ---------------------------------------------------------
            String[] record;
            SensorDb sensorDb;
            BandDb bandDb;
            while ((record = _csvReader.readRecord()) != null) {
                // retrieve the sensor db entry
                sensorDb = getSensorDb(record[0]);
                if (sensorDb == null) {
                    // is not in database yet, create and add to database
                    sensorDb = new SensorDb(record[0]);
                    _sensors.add(sensorDb);
                }

                // add the band entry to sensorDb
                bandDb = new BandDb(record[1], record[2], record[3]);
                sensorDb.addBand(bandDb);
            }
        } finally {
            in.close();
        }
    }

    /**
     * Returns the sensor coefficient file URL for a given sensor and band name.
     * Or <code>null</code> when the sensor or band are not in the database.
     *
     * @param sensor      the sensor name
     * @param band        the band name
     * @param aerosolType the aerosol type
     * @return coefficient file
     */
    public File getCoefficientFile(String sensor, String band, String aerosolType) {
        File ret = null;
        SensorDb sensorDb;

        sensorDb = getSensorDb(sensor);
        if (sensorDb != null) {
            BandDb bandDb;

            bandDb = sensorDb.getBand(band, aerosolType);
            if (bandDb == null) {
                return null;
            }

            ret = new File(_location, bandDb.getCoefficientFileName());
        }

        return ret;
    }

    ///////////////////////////////////////////////////////////////////////////
    /////// END OF PUBLIC
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Initializes the object.
     */
    private void init() {
        _sensors = new Vector<SensorDb>();
    }

    /**
     * Retrieves the sensorDb class for the given sensor name or <code>null</code> when
     * no sensor of the name is in the database.
     *
     * @param name the sensor name
     */
    private SensorDb getSensorDb(String name) {
        SensorDb ret = null;
        SensorDb current;

        for (int n = 0; n < _sensors.size(); n++) {
            current = (SensorDb) _sensors.elementAt(n);
            if (ObjectUtils.equalObjects(name, current.getName())) {
                ret = current;
            }
        }

        return ret;
    }

    ///////////////////////////////////////////////////////////////////////////
    ////// CLASS //////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Class holding the coefficient file information for one satellite sensor.
     */
    private class SensorDb {

        private String _name;
        private Vector<BandDb> _bands;

        /**
         * Creates the object with default parameters.
         */
        public SensorDb() {
            init();
        }

        /**
         * Constructs the class with given sensor name
         *
         * @param name the sensor name
         */
        public SensorDb(String name) {
            init();
            setName(name);
        }

        /**
         * Retrieves the name of the sensor.
         */
        public String getName() {
            return _name;
        }

        /**
         * Sets the name of the sensor.
         *
         * @param name the sensor name
         */
        public void setName(String name) {
            Guardian.assertNotNull("name", name);
            _name = name;
        }

        /**
         * Adds an band to the sensor.
         *
         * @param band the BandDb entry to be added
         */
        public void addBand(BandDb band) {
            Guardian.assertNotNull("bandDb", band);
            _bands.add(band);
        }

        /**
         * Retrieves the dabase entry for given band name and aerosol type.
         *
         * @param name        the band name
         * @param aerosolType the aerosol type
         */
        public BandDb getBand(String name, String aerosolType) {
            BandDb ret = null;
            BandDb current;

            for (int n = 0; n < _bands.size(); n++) {
                current = (BandDb) _bands.elementAt(n);

                if (ObjectUtils.equalObjects(name, current.getBandName())
                    && ObjectUtils.equalObjects(aerosolType, current.getAerosolType())) {
                    ret = current;
                    break;
                }
            }

            return ret;
        }

        ///////////////////////////////////////////////////////////////////////
        /////// END OF PUBLIC
        ///////////////////////////////////////////////////////////////////////

        /**
         * Initializes the object.
         */
        private void init() {
            _bands = new Vector<BandDb>();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ////// CLASS //////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Class holding the band information of a specific sensor coefficient set.
     */
    private class BandDb {

        String _bandName;
        String _aerosolType;
        String _coeffFileName;

        /**
         * Creates the object with given band name, aerosol tape and coefficient file name
         */
        public BandDb(String bandName, String aerosolType, String coefficientFileName) {
            setBandName(bandName);
            setAerosolType(aerosolType);
            setCoefficientFileName(coefficientFileName);
        }

        /**
         * Sets the band name for this object.
         *
         * @param bandName the band name
         */
        public void setBandName(String bandName) {
            Guardian.assertNotNull("bandName", bandName);
            _bandName = bandName;
        }

        /**
         * Retrieves the band name of the object.
         */
        public String getBandName() {
            return _bandName;
        }

        /**
         * Sets the aerosol type for this object.
         *
         * @param aerosolType the aerosol type
         */
        public void setAerosolType(String aerosolType) {
            Guardian.assertNotNull("aerosolType", aerosolType);
            _aerosolType = aerosolType;
        }

        /**
         * Retrieves the aerosol type for this object.
         */
        public String getAerosolType() {
            return _aerosolType;
        }

        /**
         * Sets the coefficient file name for thic object.
         *
         * @param coeffFile the coefficient file name
         */
        public void setCoefficientFileName(String coeffFile) {
            Guardian.assertNotNull("coeffFile", coeffFile);
            _coeffFileName = coeffFile;
        }

        /**
         * Retrieves the sensor coefficient file name set in this object.
         */
        public String getCoefficientFileName() {
            return _coeffFileName;
        }
    }
}
