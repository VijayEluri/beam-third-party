/*
 * $Id: HemisphReflecAccess.java,v 1.1.1.1 2005/02/15 11:13:36 meris Exp $
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

public interface HemisphReflecAccess {

    /**
     * Retrieves the coefficients for the hemispherical reflectance polynominal.
     * The array consists of
     * ret[0] = crhem.0 (offset)
     * ret[1] = crhem.1 (linear term)
     * ...
     * ret[4] = crhem.4 (fourth order)
     * @return
     */
    public double[] getHemisphReflecCoefficients();
}
