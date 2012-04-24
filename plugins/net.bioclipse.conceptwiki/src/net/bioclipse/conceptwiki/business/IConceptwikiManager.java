/*******************************************************************************
 * Copyright (c) 2012  Egon Willighagen <egonw@users.sf.net>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contact: http://www.bioclipse.net/
 ******************************************************************************/
package net.bioclipse.conceptwiki.business;

import net.bioclipse.core.PublishedClass;
import net.bioclipse.core.PublishedMethod;
import net.bioclipse.core.Recorded;
import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.StringMatrix;
import net.bioclipse.managers.business.IBioclipseManager;

@PublishedClass(
    value="Concept Wiki interaction."
)
public interface IConceptwikiManager extends IBioclipseManager {

    @Recorded
    @PublishedMethod(
        methodSummary=
            "Searches Concept Wiki for the given text.",
        params="String text"
    )
    public StringMatrix search(String text) throws BioclipseException;

}
