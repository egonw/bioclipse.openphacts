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

import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.StringMatrix;
import net.bioclipse.managers.business.IBioclipseManager;
import net.bioclipse.rdf.business.IRDFStore;
import net.bioclipse.rdf.business.RDFManager;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

public class ConceptwikiManager implements IBioclipseManager {

    private static final Logger logger = Logger.getLogger(ConceptwikiManager.class);

	private static final String QUERY_SEARCH_RESULTS =
	    "SELECT ?id ?label {" +
	    "  ?concept a <http://www.w3.org/2004/02/skos/core#Concept> ;" +
	    "   <http://purl.org/dc/elements/1.1/identifier> ?id ; " +
	    "   <http://www.w3.org/2004/02/skos/core#prefLabel> ?label ." +
	    "}";

    private static RDFManager rdf = new RDFManager();

    /**
     * Gives a short one word name of the manager used as variable name when
     * scripting.
     */
    public String getManagerName() {
        return "cw";
    }

    public StringMatrix search(String text) throws BioclipseException {
		try {
			URL searchURL = new URL(
				"http://staging.conceptwiki.org/web-ws/concept/search/?q=" +
				URLEncoder.encode(text, "UTF-8")
			);
			HttpClient client = new HttpClient();
			GetMethod method = new GetMethod(searchURL.toString());
			method.setRequestHeader("Accept", "text/turtle");
			client.executeMethod(method);
			
			String result = method.getResponseBodyAsString(); // without this things will fail??
			System.out.println(result);
			IRDFStore store = rdf.createInMemoryStore();
			rdf.importFromStream(store, method.getResponseBodyAsStream(), "TURTLE", null);
			method.releaseConnection();
			StringMatrix matrix = rdf.sparql(store, QUERY_SEARCH_RESULTS);
			method.releaseConnection();
			return matrix;
		} catch (Exception e) {
			throw new BioclipseException("Error while searching the Concept Wiki: " + e.getMessage(), e);
		}
    }

    public List<String> getAltLabels(String uuid) throws BioclipseException {
		try {
			URL searchURL = new URL(
				"http://staging.conceptwiki.org/web-ws/concept/get/?uuid=" + uuid
			);
			HttpClient client = new HttpClient();
			GetMethod method = new GetMethod(searchURL.toString());
			method.setRequestHeader("Accept", "text/turtle");
			client.executeMethod(method);
			
			String result = method.getResponseBodyAsString(); // without this things will fail??
			System.out.println(result);
			IRDFStore store = rdf.createInMemoryStore();
			rdf.importFromStream(store, method.getResponseBodyAsStream(), "TURTLE", null);
			method.releaseConnection();

			String query_labels =
				"SELECT ?label {" +
			    "  ?concept a <http://www.w3.org/2004/02/skos/core#Concept> ;" +
			    "   <http://purl.org/dc/elements/1.1/identifier> ?id ; " +
			    "   <http://www.w3.org/2004/02/skos/core#prefLabel> ?label ." +
			    "}";
			StringMatrix matrix = rdf.sparql(store, QUERY_SEARCH_RESULTS);
			method.releaseConnection();
	    	return matrix.getColumn("label");
		} catch (Exception e) {
			throw new BioclipseException("Error while searching the Concept Wiki: " + e.getMessage(), e);
		}
    }
}
