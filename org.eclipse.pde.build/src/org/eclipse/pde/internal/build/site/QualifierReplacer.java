/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.pde.internal.build.site;

import java.util.Calendar;
import java.util.Properties;
import org.eclipse.pde.internal.build.IBuildPropertiesConstants;

public class QualifierReplacer implements IBuildPropertiesConstants {
	private static final String DOT_QUALIFIER = '.' + PROPERTY_QUALIFIER;
	public static String replaceQualifierInVersion(String version, String id, String replaceTag, Properties newVersions) {
		if (replaceTag == null)
			return null;

		if (!version.endsWith(DOT_QUALIFIER))
			return null;

		String newQualifier = null;
		if (replaceTag.equalsIgnoreCase(PROPERTY_CONTEXT)) {
			newQualifier = (String) newVersions.get(id);
			if (newQualifier == null)
				newQualifier = '.' + getDate();
		} else if (replaceTag.equalsIgnoreCase(PROPERTY_NONE)) {
			newQualifier = ""; //$NON-NLS-1$
		} else {
			newQualifier = '.' + replaceTag;
		}

		return version.replaceFirst(DOT_QUALIFIER, newQualifier);
	}

	private static String getDate() {
		final String empty = ""; //$NON-NLS-1$
		int monthNbr = Calendar.getInstance().get(Calendar.MONTH) + 1;
		String month = (monthNbr < 10 ? "0" : empty) + monthNbr; //$NON-NLS-1$

		int dayNbr = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
		String day = (dayNbr < 10 ? "0" : empty) + dayNbr; //$NON-NLS-1$

		int hourNbr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		String hour = (hourNbr < 10 ? "0" : empty) + hourNbr; //$NON-NLS-1$

		int minuteNbr = Calendar.getInstance().get(Calendar.MINUTE);
		String minute = (minuteNbr < 10 ? "0" : empty) + minuteNbr; //$NON-NLS-1$

		return empty + Calendar.getInstance().get(Calendar.YEAR) + month + day + hour + minute; //$NON-NLS-1$
	}
}