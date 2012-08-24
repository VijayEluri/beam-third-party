package wew.water.gpf;

import java.awt.Color;
import java.io.IOException;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.framework.processor.ProcessorConstants;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;
import wew.water.WaterProcessorOzone;

@OperatorMetadata(alias = "WaterProcessorOp",
                  version = "1.0",
                  description = "FUB/WeW WATER Processor GPF-Operator")
public class WaterProcessorOp extends PixelOperator {

    private final static String[] output_concentration_band_names = {
                "algal_2",
                "yellow_subs",
                "total_susp"
    };
    private final static String[] output_optical_depth_band_names = {
                "aero_opt_thick_440",
                "aero_opt_thick_550",
                "aero_opt_thick_670",
                "aero_opt_thick_870"
    };
    private final static String[] output_reflectance_band_names = {
                "reflec_1",
                "reflec_2",
                "reflec_3",
                "reflec_4",
                "reflec_5",
                "reflec_6",
                "reflec_7",
                "reflec_9"
    };

    private static String[] output_concentration_band_descriptions = {
                "Chlorophyll 2 content",
                "Yellow substance",
                "Total suspended matter"
    };
    private static String[] output_optical_depth_band_descriptions = {
                "Aerosol optical thickness",
                "Aerosol optical thickness",
                "Aerosol optical thickness",
                "Aerosol optical thickness"
    };

    private static String[] output_reflectance_band_descriptions = {
                "RS reflectance",
                "RS reflectance",
                "RS reflectance",
                "RS reflectance",
                "RS reflectance",
                "RS reflectance",
                "RS reflectance",
                "RS reflectance"
    };

    private static String[] output_concentration_band_units = {
                "log10(mg/m^3)",
                "log10(1/m)",
                "log10(g/m^3)"
    };

    private static String[] output_optical_depth_band_units = {
                "1",
                "1",
                "1",
                "1"
    };

    private static String[] output_reflectance_band_units = {
                "1/sr",
                "1/sr",
                "1/sr",
                "1/sr",
                "1/sr",
                "1/sr",
                "1/sr",
                "1/sr"
    };

    // Wavelengths for the water leaving reflectances rho_w
    private static float[] tau_lambda = {
                440.00f, 550.00f, 670.00f, 870.00f
    };

    // Wavelengths for the water leaving reflectances rho_w
    private static float[] rho_w_lambda = {
                412.50f, 442.50f, 490.00f, 510.00f,
                560.00f, 620.00f, 665.00f, 708.75f
    };

    // Bandwidth for the water leaving reflectances rho_w
    private static float[] rho_w_bandw = {
                10.00f, 10.00f, 10.00f, 10.00f,
                10.00f, 10.00f, 10.00f, 10.00f
    };

    private static final String result_flags_name = "result_flags";

    // Mask value to be written if inversion fails
    private static final float result_mask_value = 5.0f;

    private static final String[] result_error_texts = {
                "Pixel was a priori masked out",
                "CHL retrieval failure (input)",
                "CHL retrieval failure (output)",
                "YEL retrieval failure (input)",
                "YEL retrieval failure (output)",
                "TSM retrieval failure (input)",
                "TSM retrieval failure (output)",
                "Atmospheric correction failure (input)",
                "Atmospheric correction failure (output)"
    };

    private static final String[] result_error_names = {
                "LEVEL1b_MASKED",
                "CHL_IN",
                "CHL_OUT",
                "YEL_IN",
                "YEL_OUT",
                "TSM_IN",
                "TSM_OUT",
                "ATM_IN",
                "ATM_OUT"
    };
    public static final int[] RESULT_ERROR_VALUES = {
                0x00000001,
                0x00000002,
                0x00000004,
                0x00000008,
                0x00000010,
                0x00000020,
                0x00000040,
                0x00000080,
                0x00000100
    };

    private static final float[] result_error_transparencies = {
                0.0f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f,
                0.5f
    };

    private static final int GLINT_RISK = 0x00000004;
    private static final int SUSPECT = 0x00000008;
    private static final int BRIGHT = 0x00000020;
    private static final int INVALID = 0x00000080;

    private static final int MASK_TO_BE_USED = (GLINT_RISK | BRIGHT | INVALID);

    @SourceProduct(label = "Select source product",
                   description = "The MERIS L1b source product used for the processing.")
    private Product sourceProduct;

    private int maskToBeUsed;
    private float[] solarFlux;

    private final static String[] source_raster_names = new String[]{
                EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME,  // source sample index  0   radiance_1
                EnvisatConstants.MERIS_L1B_RADIANCE_2_BAND_NAME,  // source sample index  1   radiance_2
                EnvisatConstants.MERIS_L1B_RADIANCE_3_BAND_NAME,  // source sample index  2   radiance_3
                EnvisatConstants.MERIS_L1B_RADIANCE_4_BAND_NAME,  // source sample index  3   radiance_4
                EnvisatConstants.MERIS_L1B_RADIANCE_5_BAND_NAME,  // source sample index  4   radiance_5
                EnvisatConstants.MERIS_L1B_RADIANCE_6_BAND_NAME,  // source sample index  5   radiance_6
                EnvisatConstants.MERIS_L1B_RADIANCE_7_BAND_NAME,  // source sample index  6   radiance_7
                EnvisatConstants.MERIS_L1B_RADIANCE_8_BAND_NAME,  // source sample index  7   radiance_8
                EnvisatConstants.MERIS_L1B_RADIANCE_9_BAND_NAME,  // source sample index  8   radiance_9
                EnvisatConstants.MERIS_L1B_RADIANCE_10_BAND_NAME, // source sample index  9   radiance_10
                EnvisatConstants.MERIS_L1B_RADIANCE_11_BAND_NAME, // source sample index 10   radiance_11
                EnvisatConstants.MERIS_L1B_RADIANCE_12_BAND_NAME, // source sample index 11   radiance_12
                EnvisatConstants.MERIS_L1B_RADIANCE_13_BAND_NAME, // source sample index 12   radiance_13
                EnvisatConstants.MERIS_L1B_RADIANCE_14_BAND_NAME, // source sample index 13   radiance_14
                EnvisatConstants.MERIS_L1B_RADIANCE_15_BAND_NAME, // source sample index 14   radiance_15
                EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME,         // source sample index 15   l1_flags
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[6],   // source sample index 16   sun_zenith
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[7],   // source sample index 17   sun_azimuth
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[8],   // source sample index 18   view_zenith
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[9],   // source sample index 19   view_azimuth
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[10],  // source sample index 20   zonal_wind
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[11],  // source sample index 21   merid_wind
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[12],  // source sample index 22   atm_press
                EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES[13]   // source sample index 23   ozone
    };
    private final static int source_sample_index_l1b_flags = 15;
    private final static int source_sample_index_sun_zenith = 16;
    private final static int source_sample_index_sun_azimuth = 17;
    private final static int source_sample_index_view_zenith = 18;
    private final static int source_sample_index_view_azimuth = 19;
    private final static int source_sample_index_zonal_wind = 20;
    private final static int source_sample_index_merid_wind = 21;
    private final static int source_sample_index_atm_press = 22;
    private final static int source_sample_index_ozone = 23;

    private Band[] inputBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];


    @Override
    protected void computePixel(final int xpos, final int ypos, Sample[] sourceSamples, WritableSample[] targetSamples) {

        // If set to -1.0f : NN input and output ranges are checked
        // If set to +1.0f : NN input and output ranges are NOT checked
        float aset = -1.0f;

        int width = 1;
        int nbands = inputBands.length;

        float[] sza = new float[width];
        float[] saa = new float[width];
        float[] vza = new float[width];
        float[] vaa = new float[width];
        float[] zw = new float[width];
        float[] mw = new float[width];
        float[] press = new float[width];
        float[] o3 = new float[width];

        // allocate memory for a multispectral scan line
        float[][] toa = new float[nbands][width];
        int[] l1Flags = new int[width];

        // allocate memory for the flags
        int[] resultFlags = new int[width];
        int[] resultFlagsNN = new int[width];

        // Ozone data
        double[] wavelength = new double[nbands];
        double[] exO3 = new double[nbands];

        // local variables
        double d2r;
        float dazi;
        int k;
        int l;
        int ls = 0;
        int n;
        float[] a = new float[width];

        int inodes;
        int onodes;
        int onodes_1;
        int onodes_2;
        int stage;

        float[][] ipixel = new float[2][1];
        float[][] ipixels = new float[2][1];
        float[][] opixel = new float[2][1];

        // Load the wavelengths and ozone spectral extinction coefficients
        //
        for (int i = 0; i < nbands; i++) {
            wavelength[i] = inputBands[i].getSpectralWavelength();
            exO3[i] = WaterProcessorOzone.O3excoeff(wavelength[i]);
        }

        d2r = Math.acos(-1.0) / 180.0;

        // Get the number of I/O nodes in advance
        inodes = NN_YellowSubstance.compute(ipixel, -1, opixel, 1, width, resultFlags, 0, a);
        // implicit atm.corr.
        onodes_1 = NN_YellowSubstance.compute(ipixel, 1, opixel, -1, width, resultFlags, 0, a);
        // explicit atm.corr.
        onodes_2 = NN_AtmCorr.compute(ipixel, 1, opixel, -1, width, resultFlags, 0, a);

        int num_toa = 12;
        int output_planes = output_concentration_band_names.length +
                            output_optical_depth_band_names.length + output_reflectance_band_names.length;
        float[][] top = new float[num_toa][width];
        float[][] tops = new float[num_toa][width];
        double[][] o3f = new double[num_toa][width];
        float[][] sof = new float[num_toa][width];
        float[][] aux = new float[2][width];
        float[][] geo = new float[4][width];
        float[][] result = new float[output_planes][width];


        // First the TOA radiances
        for (n = 0; n < inputBands.length; n++) {
            toa[n][0] = sourceSamples[n].getFloat();
        } // n


        // Second the flags
        l1Flags[0] = sourceSamples[source_sample_index_l1b_flags].getInt();

        // Third the auxiliary data
        sza[0] = sourceSamples[source_sample_index_sun_zenith].getFloat();
        saa[0] = sourceSamples[source_sample_index_sun_azimuth].getFloat();
        vza[0] = sourceSamples[source_sample_index_view_zenith].getFloat();
        vaa[0] = sourceSamples[source_sample_index_view_azimuth].getFloat();
        zw[0] = sourceSamples[source_sample_index_zonal_wind].getFloat();
        mw[0] = sourceSamples[source_sample_index_merid_wind].getFloat();
        press[0] = sourceSamples[source_sample_index_atm_press].getFloat();
        o3[0] = sourceSamples[source_sample_index_ozone].getFloat();

        final int x = 0;

        resultFlags[x] = 0;
        resultFlagsNN[x] = 0;

        // Exclude pixels from processing if the following l1flags mask becomes true
        k = l1Flags[x] & maskToBeUsed;
        if (k != 0) {
            resultFlags[x] = RESULT_ERROR_VALUES[0];
        }

        // *********************
        // * STAGE 0
        // *********************

        // Get the toa reflectances for selected bands
        // and normalize ozone (ozone_norm is fixed to TRUE)
        //
        l = 0;
        final double TOTAL_OZONE_DU_MOMO = 344.0;
        for (n = 0; n <= 6; n++, l++) {
            tops[l][x] = toa[n][x];
            sof[l][x] = solarFlux[n];
            top[l][x] = toa[n][x] / solarFlux[n];
            o3f[l][x] = Math.exp(-(TOTAL_OZONE_DU_MOMO - o3[x]) * exO3[n] / 1000.0 * (1.0 / Math.cos(
                        (double) vza[x] * d2r) + 1.0 / Math.cos((double) sza[x] * d2r)));
            top[l][x] *= o3f[l][x];
        }
        for (n = 8; n <= 9; n++, l++) {
            tops[l][x] = toa[n][x];
            sof[l][x] = solarFlux[n];
            top[l][x] = toa[n][x] / solarFlux[n];
            o3f[l][x] = Math.exp(-(TOTAL_OZONE_DU_MOMO - o3[x]) * exO3[n] / 1000.0 * (1.0 / Math.cos(
                        (double) vza[x] * d2r) + 1.0 / Math.cos((double) sza[x] * d2r)));
            top[l][x] *= o3f[l][x];
        }
        for (n = 11; n <= 13; n++, l++) {
            tops[l][x] = toa[n][x];
            sof[l][x] = solarFlux[n];
            top[l][x] = toa[n][x] / solarFlux[n];
            o3f[l][x] = Math.exp(-(TOTAL_OZONE_DU_MOMO - o3[x]) * exO3[n] / 1000.0 * (1.0 / Math.cos(
                        (double) vza[x] * d2r) + 1.0 / Math.cos((double) sza[x] * d2r)));
            top[l][x] *= o3f[l][x];
        }

        // Prepare some auxiliary vectors
        //

        // Get the wind speed
        aux[0][x] = (float) Math.sqrt((double) (zw[x] * zw[x] + mw[x] * mw[x]));
        // Get the pressure
        aux[1][x] = press[x];

        // Adjust the azimuth difference
        dazi = vaa[x] - saa[x];

        while (dazi <= -180.0f) {
            dazi += 360.0f;
        }
        while (dazi > 180.0f) {
            dazi -= 360.0f;
        }
        float tmp = dazi;
        if (tmp >= 0.0f) {
            dazi = +180.0f - dazi;
        }
        if (tmp < 0.0f) {
            dazi = -180.0f - dazi;
        }

        // Get cos(sunzen)
        geo[0][x] = (float) Math.cos((double) sza[x] * d2r);

        // And now transform into cartesian coordinates
        geo[1][x] = (float) (Math.sin((double) vza[x] * d2r) * Math.cos((double) dazi * d2r)); // obs_x
        geo[2][x] = (float) (Math.sin((double) vza[x] * d2r) * Math.sin((double) dazi * d2r)); // obs_y
        geo[3][x] = (float) (Math.cos((double) vza[x] * d2r));                            // obs_z

        // *********************
        // * STAGE 1-4
        // *********************

        onodes = onodes_1; // They differ !!

        ipixel = new float[inodes][width];
        ipixels = new float[inodes][width];
        opixel = new float[onodes][width];

        // load the TOA reflectances
        for (l = 0; l < num_toa; l++) {
            ipixel[l][x] = top[l][x];
        }

        // get the wind speed and pressure
        ipixel[l++][x] = aux[0][x];
        ipixel[l++][x] = aux[1][x];

        // get cos(sunzen), x, yPos, z
        ipixel[l++][x] = geo[0][x];
        ipixel[l++][x] = geo[1][x];
        ipixel[l++][x] = geo[2][x];
        ipixel[l++][x] = geo[3][x];

        // Check against range limits inside the network
        // recall if the value of a[x] is set to -1.0f.
        //
        // This results in the application of the flag
        // 'RESULT_ERROR_VALUE[]' to the 'resultFlagsNN'
        a[x] = aset;

        // Save input pixel
        ls = l;
        for (l = 0; l < ls; l++) {
            ipixels[l][x] = ipixel[l][x];
        }


        // Run the 1-step chlorophyll network;
        stage = 1;
        NN_CHL.compute(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);
//            recallBCK.lrecall_run46_C2_100_nn(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);

        // Input range failure
        if ((a[x] > -2.1) && (a[x] < -1.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
        }
        // Output range failure
        if ((a[x] > -19.1) && (a[x] < -18.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        // Input AND Output range failure
        if ((a[x] > -22.1) && (a[x] < -21.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        result[0][x] = opixel[0][x];

        // Run the 1-step yellow substance network;
        stage = 2;
        // reload the pixel
        for (l = 0; l < ls; l++) {
            ipixel[l][x] = ipixels[l][x];
        }
        a[x] = aset;

        NN_YellowSubstance.compute(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);
//            recallBCK.lrecall_run38_C2_040_nn(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);

        // Input range failure
        if ((a[x] > -2.1) && (a[x] < -1.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
        }
        // Output range failure
        if ((a[x] > -19.1) && (a[x] < -18.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        // Input AND Output range failure
        if ((a[x] > -22.1) && (a[x] < -21.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        result[1][x] = opixel[0][x];

        // Run the 1-step total suspended matter network;
        stage = 3;

        // reload the pixel
        for (l = 0; l < ls; l++) {
            ipixel[l][x] = ipixels[l][x];
        }
        a[x] = aset;

        NN_TSM.compute(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);
//            recallBCK.lrecall_run39_C2_080_nn(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);

        // Input range failure
        if ((a[x] > -2.1) && (a[x] < -1.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
        }
        // Output range failure
        if ((a[x] > -19.1) && (a[x] < -18.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        // Input AND Output range failure
        if ((a[x] > -22.1) && (a[x] < -21.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        result[2][x] = opixel[0][x];

        // Run part 1 of the 2-step atm.corr. network;
        stage = 4;
        onodes = onodes_2;
        opixel = new float[onodes][width];

        // reload the pixel
        for (l = 0; l < ls; l++) {
            ipixel[l][x] = ipixels[l][x];
        }
        a[x] = aset;

        NN_AtmCorr.compute(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);
//            recallBCK.lrecall_run19_C2_080_nn(ipixel, inodes, opixel, onodes, width, resultFlags, 0, a);

        //System.out.print("--" + yPos + "--" + x + "--> " + a[x] + " : " + resultFlagsNN[x]);
        // Input range failure
        if ((a[x] > -2.1) && (a[x] < -1.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
        }
        // Output range failure
        if ((a[x] > -19.1) && (a[x] < -18.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        // Input AND Output range failure
        if ((a[x] > -22.1) && (a[x] < -21.9)) {
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage - 1];
            resultFlagsNN[x] |= RESULT_ERROR_VALUES[2 * stage];
        }
        //System.out.println(" --> " + resultFlagsNN[x]);

        // The aots
        final int num_msl = 8;
        final int numOfConcentrationBands = 3;
        for (int i = num_msl; i < onodes; i++) {
            result[numOfConcentrationBands + i - num_msl][x] = opixel[i][x];
        }
        for (int i = 0; i < num_msl; i++) {
            final int numOfSpectralAerosolOpticalDepths = 4;
            result[numOfConcentrationBands + numOfSpectralAerosolOpticalDepths + i][x] = opixel[i][x];
        }

        // OK, the whole scanline had been processed. Now check for error flags !
        // If set, set output vector to mask value !
        if (resultFlags[x] != 0) {
            for (n = 0; n < output_planes; n++) {
                result[n][x] = (float) result_mask_value;
            }
        }
        // Combine result flags
        resultFlags[x] |= resultFlagsNN[x];

        // Write the result planes
        //
        for (n = 0; n < output_planes; n++) {
            targetSamples[n].set(result[n][0]);
        }
        // write the flag planes
        targetSamples[output_planes].set(l1Flags[0]);
        targetSamples[output_planes + 1].set(resultFlags[0]);
    }

    private void checkWhetherSuspectIsValid() {
        final int height = sourceProduct.getSceneRasterHeight();
        final int width = sourceProduct.getSceneRasterWidth();

        int k;// Some Level 1b scenes mark almost all pixels as 'SUSPECT'. This is obviously nonsense.
        // Because we would like to make use of the SUSPECT flag in mask MASK_TO_BE_USED we do
        // check first if it behaves fine, ie the number of suspect pixels for one line in the
        // middle of the scene should be below 50 % . Else we do not make use of the SUSPECT flag
        // in the mask MASK_TO_BE_USED.

        // Grab a line in the middle of the scene
        final Band l1FlagsInputBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        int[] l1Flags = new int[width];
        try {
            l1FlagsInputBand.readPixels(0, height / 2, width, 1, l1Flags);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // The input type pattern for ICOL products
        final String ICOL_PATTERN = "MER_.*1N";
        boolean icolMode = sourceProduct.getProductType().matches(ICOL_PATTERN);
        if (icolMode) {
            maskToBeUsed = MASK_TO_BE_USED;
            System.out.println("--- Input product is of type icol ---");
            System.out.println("--- Switching to relaxed mask. ---");
        } else {
            maskToBeUsed = SUSPECT;
            k = 0;
            // Now sum up the cases which signal a SUSPECT behaviour
            for (int i = 0; i < width; i++) {
                if ((l1Flags[i] & maskToBeUsed) != 0) {
                    k++;
                }
            }
            // lower than 50 percent ?
            if (k < width / 2)
            // Make use of the SUSPECT flag
            {
                maskToBeUsed = MASK_TO_BE_USED | SUSPECT;
            } else {
                // Forget it ....
                maskToBeUsed = MASK_TO_BE_USED;
                final float percent = (float) k / (float) width * 100.0f;
                System.out.println("--- " + percent + " % of the scan line are marked as SUSPECT ---");
                System.out.println("--- Switching to relaxed mask. ---");
            }
        }
    }

    /*
    * Reads the solar spectral fluxes for all MERIS L1b bands.
    *
    * Sometimes the file do not contain solar fluxes. As they do
    * show heavy variations over the year or for slight wavelength
    * shifts we do use some defaults if necessary.
    */
    private float[] getSolarFlux(Product product, Band[] bands) {
        float[] dsf = getSolarFluxFromMetadata(product);
        if (dsf == null) {
            dsf = new float[bands.length];
            final double[] defsol = new double[]{
                        1670.5964, 1824.1444, 1874.9883,
                        1877.6682, 1754.7749, 1606.6401,
                        1490.0026, 1431.8726, 1369.2035,
                        1231.7164, 1220.0767, 1144.9675,
                        932.3497, 904.8193, 871.0908
            };
            for (int i = 0; i < bands.length; i++) {
                Band band = bands[i];
                dsf[i] = band.getSolarFlux();
                if (dsf[i] <= 0.0) {
                    dsf[i] = (float) defsol[i];
                }
            }
        }
        return dsf;
    }

    private float[] getSolarFluxFromMetadata(Product product) {
        MetadataElement metadataRoot = product.getMetadataRoot();
        MetadataElement gadsElem = metadataRoot.getElement("Scaling_Factor_GADS");
        if (gadsElem != null) {
            MetadataAttribute solarFluxAtt = gadsElem.getAttribute("sun_spec_flux");
            if (solarFluxAtt != null) {
                return (float[]) solarFluxAtt.getDataElems();
            }
        }
        return null;
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();
        for (int i = 0; i < inputBands.length; i++) {
            String radianceBandName = "radiance_" + (i + 1);
            Band radianceBand = sourceProduct.getBand(radianceBandName);
            if (radianceBand == null) {
                throw new OperatorException(String.format("Missing input band '%s'.", radianceBandName));
            }
            if (radianceBand.getSpectralWavelength() <= 0.0) {
                throw new OperatorException(String.format("Input band '%s' does not have wavelength information.", radianceBandName));
            }
            inputBands[i] = radianceBand;
        }
        solarFlux = getSolarFlux(sourceProduct, inputBands);
        checkWhetherSuspectIsValid();
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);
        final Product sourceProduct = productConfigurer.getSourceProduct();
        final Product targetProduct = productConfigurer.getTargetProduct();

        targetProduct.setProductType(getOutputProductTypeSafe());

        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        addConcentrationBands(targetProduct, sceneWidth, sceneHeight);
        addOpticalDepthBands(targetProduct, sceneWidth, sceneHeight);
        addReflectanceBands(targetProduct, sceneWidth, sceneHeight);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, false);
        productConfigurer.copyBands(EnvisatConstants.MERIS_AMORGOS_L1B_CORR_LATITUDE_BAND_NAME);
        productConfigurer.copyBands(EnvisatConstants.MERIS_AMORGOS_L1B_CORR_LONGITUDE_BAND_NAME);
        productConfigurer.copyBands(EnvisatConstants.MERIS_AMORGOS_L1B_ALTIUDE_BAND_NAME);

        FlagCoding resultFlagCoding = createResultFlagCoding();
        targetProduct.getFlagCodingGroup().add(resultFlagCoding);
        final Band resultFlagsOutputBand = targetProduct.addBand(result_flags_name, ProductData.TYPE_UINT16);
        resultFlagsOutputBand.setDescription("FUB/WeW WATER plugin specific flags");
        resultFlagsOutputBand.setSampleCoding(resultFlagCoding);

        productConfigurer.copyMasks();

        String flagNamePrefix = result_flags_name + ".";
        addMasksToTargetProduct(targetProduct, sceneWidth, sceneHeight, flagNamePrefix);
        productConfigurer.copyMetadata();
    }

    private void addMasksToTargetProduct(Product targetProduct, int sceneWidth, int sceneHeight, String flagNamePrefix) {
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        Color[] colors = new Color[]{
                    Color.cyan, Color.green, Color.green, Color.yellow, Color.yellow,
                    Color.orange, Color.orange, Color.blue, Color.blue
        };
        for (int i = 0; i < result_error_names.length; i++) {
            maskGroup.add(Mask.BandMathsType.create(result_error_names[i].toLowerCase(), result_error_texts[i],
                                                    sceneWidth, sceneHeight, flagNamePrefix + result_error_names[i],
                                                    colors[i], result_error_transparencies[i]));

        }
    }

    private void addReflectanceBands(Product targetProduct, int sceneWidth, int sceneHeight) {
        for (int i = 0; i < output_reflectance_band_names.length; i++) {
            final Band band = createBand(output_reflectance_band_names[i], sceneWidth, sceneHeight);
            band.setDescription(output_reflectance_band_descriptions[i]);
            band.setUnit(output_reflectance_band_units[i]);
            band.setSpectralWavelength(rho_w_lambda[i]);
            band.setSpectralBandwidth(rho_w_bandw[i]);
            band.setSpectralBandIndex(i);
            band.setNoDataValue(result_mask_value);
            band.setNoDataValueUsed(true);
            targetProduct.addBand(band);
        }
    }

    private void addOpticalDepthBands(Product targetProduct, int sceneWidth, int sceneHeight) {
        for (int i = 0; i < output_optical_depth_band_names.length; i++) {
            final Band band = createBand(output_optical_depth_band_names[i], sceneWidth, sceneHeight);
            band.setDescription(output_optical_depth_band_descriptions[i]);
            band.setUnit(output_optical_depth_band_units[i]);
            band.setSpectralWavelength(tau_lambda[i]);
            band.setSpectralBandIndex(i);
            band.setNoDataValue(result_mask_value);
            band.setNoDataValueUsed(true);
            targetProduct.addBand(band);
        }
    }

    private void addConcentrationBands(Product targetProduct, int sceneWidth, int sceneHeight) {
        for (int i = 0; i < output_concentration_band_names.length; i++) {
            final Band band = createBand(output_concentration_band_names[i], sceneWidth, sceneHeight);
            band.setDescription(output_concentration_band_descriptions[i]);
            band.setUnit(output_concentration_band_units[i]);
            targetProduct.addBand(band);
        }
    }

    private Band createBand(String bandName, int sceneWidth, int sceneHeight) {
        final Band band = new Band(bandName, ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);
        band.setScalingOffset(0.0);
        band.setScalingFactor(1.0);
        band.setSpectralBandIndex(0);
        return band;
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        configureSamples(sampleConfigurer, source_raster_names);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        String[] bandNames = new String[0];
        bandNames = StringUtils.addArrays(bandNames, output_concentration_band_names);
        bandNames = StringUtils.addArrays(bandNames, output_optical_depth_band_names);
        bandNames = StringUtils.addArrays(bandNames, output_reflectance_band_names);
        bandNames = StringUtils.addToArray(bandNames, EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        bandNames = StringUtils.addToArray(bandNames, result_flags_name);
        configureSamples(sampleConfigurer, bandNames);
    }

    private void configureSamples(SampleConfigurer sampleConfigurer, String[] bandNames) {
        for (int i = 0; i < bandNames.length; i++) {
            sampleConfigurer.defineSample(i, bandNames[i]);
        }
    }

    public static FlagCoding createResultFlagCoding() {
        FlagCoding resultFlagCoding = new FlagCoding(result_flags_name);
        resultFlagCoding.setDescription("RESULT Flag Coding");
        for (int i = 0; i < result_error_names.length; i++) {
            MetadataAttribute attribute = new MetadataAttribute(result_error_names[i], ProductData.TYPE_INT32);
            attribute.getData().setElemInt(RESULT_ERROR_VALUES[i]);
            attribute.setDescription(result_error_texts[i]);
            resultFlagCoding.addAttribute(attribute);
        }
        return resultFlagCoding;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WaterProcessorOp.class);
        }
    }

    /*
    * Retrieves the output product type from the input product type by appending "_FLH_MCI" to the type string.
    *
    * @throws org.esa.beam.framework.processor.ProcessorException
    *          when an error occurs
    */
    private String getOutputProductTypeSafe() throws OperatorException {
        String productType = sourceProduct.getProductType();
        if (productType == null) {
//            @todo retrieve message not from ProcessorConstants
            throw new OperatorException(ProcessorConstants.LOG_MSG_NO_INPUT_TYPE);
        }

        return productType + "_FLH_MCI";
    }

}
