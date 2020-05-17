/*
 * Copyright (C) 2006 Steve Ratcliffe
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *
 * Author: Steve Ratcliffe
 * Create date: 03-Dec-2006
 */
package ch.bubendorf.hgt;

/**
 * Some miscellaneous functions that are used within the .img code.
 *
 * @author Steve Ratcliffe
 */
public class Utils {
	private final static double DELTA = 360.0D / (1 << 24) / 2; //Correct rounding

	/**
	 * A map unit is an integer value that is 1/(2^24) degrees of latitude or
	 * longitude.
	 *
	 * @param l The lat or long as decimal degrees.
	 * @return An integer value in map units.
	 */
	public static int toMapUnit(double l) {

		if (l > 0)
			return (int) ((l + DELTA) * (1 << 24) / 360);
		else
			return (int) ((l - DELTA) * (1 << 24) / 360);
	}

	/**
	 * Convert an angle in map units to degrees.
	 */
	public static double toDegrees(int val) {
		return (double) val * (360.0 / (1 << 24));
	}

	/**
	 * Convert an angle in map units to radians.
	 */
	public static double toRadians(int mapunits) {
		return toDegrees(mapunits) * (Math.PI / 180);
	}

}

