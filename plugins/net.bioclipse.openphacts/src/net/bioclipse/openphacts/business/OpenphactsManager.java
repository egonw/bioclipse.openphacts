/*******************************************************************************
 * Copyright (c) 2012  Ola Spjuth <ola.spjuth@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contact: http://www.bioclipse.net/
 ******************************************************************************/
package net.bioclipse.openphacts.business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.bioclipse.cdk.business.ICDKManager;
import net.bioclipse.cdk.domain.ICDKMolecule;
import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.IMolecule;
import net.bioclipse.core.domain.StringMatrix;
import net.bioclipse.managers.business.IBioclipseManager;
import net.bioclipse.openphacts.model.CWResult;
import net.bioclipse.rdf.business.IRDFStore;
import net.bioclipse.rdf.business.RDFManager;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import LDA_API.ConceptWikiDataAccessJava;
import LDA_API.LDAInfo;
import LDA_API.OPSLDAJava;

import com.github.egonw.ops4j.Mapping;

/**
 * A manager to query the Open PHACTS infrastructure.
 * @author ola
 *
 */
public class OpenphactsManager implements IBioclipseManager {

	private static final Logger logger = Logger.getLogger(OpenphactsManager.class);
	public static final String OPENPHACTS_PREFERENCE_NODE ="openphacts.prefs.node";
	public static final String OPENPHACTS_ENDPOINT_PREFERENCE ="openphacts.prefs.endpoint";
	public static final String CONCEPTWIKI_ENDPOINT_PREFERENCE ="conceptwiki.prefs.endpoint";

	private static final String EXACT_MATCHES =
		"PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
		"SELECT ?match WHERE {" +
	    " ?concept skos:exactMatch ?match ." +
		"}"
	;

	RDFManager rdf = new RDFManager();
	
	public String getManagerName() {
		return "openphacts";
	}

	//Get and set OPS and CW endpoint preferences via manager methods
	private String getConceptWikiEndpoint(){
		 IEclipsePreferences preferences = ConfigurationScope.INSTANCE
				  .getNode(OPENPHACTS_PREFERENCE_NODE);
		 return preferences.get(CONCEPTWIKI_ENDPOINT_PREFERENCE,"default");
	}
	private String getOPSLDAendpoint(){
		 IEclipsePreferences preferences = ConfigurationScope.INSTANCE
				  .getNode(OPENPHACTS_PREFERENCE_NODE);
		 return preferences.get(OPENPHACTS_ENDPOINT_PREFERENCE,"default");
	}
	public void setConceptWikiEndpoint(String endpoint){
		 IEclipsePreferences preferences = ConfigurationScope.INSTANCE
				  .getNode(OPENPHACTS_PREFERENCE_NODE);
		 preferences.put(CONCEPTWIKI_ENDPOINT_PREFERENCE,endpoint);
	}
	public void setOPSLDAendpoint(String endpoint){
		 IEclipsePreferences preferences = ConfigurationScope.INSTANCE
				  .getNode(OPENPHACTS_PREFERENCE_NODE);
		 preferences.put(OPENPHACTS_ENDPOINT_PREFERENCE,endpoint);
	}

	/**
	 * Look up proteins in conceptwiki
	 * 
	 * @param query Query string to search for.
	 * @return list of CWresults (name + cwid)
	 * @throws BioclipseException 
	 */
	public List<CWResult> lookUpProteins(String query, IProgressMonitor monitor) throws BioclipseException {
		return lookUpCW(query, "protein", monitor);
	}		

	/**
	 * Look up compounds in conceptwiki
	 * 
	 * @param query Query string to search for.
	 * @return list of CWresults (name + cwid)
	 * @throws BioclipseException 
	 */
	public List<CWResult> lookUpCompounds(String query, IProgressMonitor monitor)
			throws BioclipseException {
		return lookUpCW(query, "compound", monitor);
	}

	public List<String> mapURI(String URI, IProgressMonitor monitor)
			throws BioclipseException {
		Mapping mapper;
		try {
			mapper = Mapping.getInstance(
				getOPSLDAendpoint(), "5dea5f60", "064e38c33ad32e925cd7a6e78b7c4996"
			);
			String rdfContent = mapper.mapUri(URI);
			System.out.println("OPS LDA results: " + rdfContent);
			IRDFStore store = rdf.createInMemoryStore();
			rdf.importFromString(store, rdfContent, "Turtle", monitor);
			StringMatrix matches = rdf.sparql(store, EXACT_MATCHES);
			return matches.getColumn("match");
		} catch (Exception e) {
			throw new BioclipseException(e.getMessage(), e);
		}
	}
	
	/**
	 * Private method for CW access
	 */
	private List<CWResult> lookUpCW(String name, String type, IProgressMonitor monitor)
			throws BioclipseException {
		
		if (name==null || name.length()<3)
			throw new BioclipseException(
							"Searches need to be at least 3 characters long");

		monitor.beginTask("Searching ConceptWiki for:" + name, IProgressMonitor.UNKNOWN);
		monitor.subTask("Searching ConceptWiki for:" + name);

		//Query CW based on type
		List<Map<String, String>> collection=null;
		if ("compound".equals(type))
			collection = ConceptWikiDataAccessJava.CW_Search_Compound(
						name, getConceptWikiEndpoint());

		else if ("protein".equals(type))
			collection = ConceptWikiDataAccessJava.CW_Search_Protein(
					name, getConceptWikiEndpoint());

		else throw new BioclipseException("Type must be either protein or compound");

		//Parse the results
		List<CWResult> res = new ArrayList<CWResult>();
		for (Map<String, String> result : collection){

			String cwid = result.get("uuid");
			String matchname = result.get("matchstring");
			
			CWResult cmodel = new CWResult(matchname, cwid);
			res.add(cmodel);
		}

		return res;
	}

	/**
	 * Look up target information for a set of CWResults by CWID.
	 * 
	 * @param collection
	 * @param monitor
	 * @return
	 */
	public List<String> getProteinInfo(List<CWResult> collection, IProgressMonitor monitor){

		monitor.beginTask("Retrieving information about protein from Open PHACTS", collection.size());
		List<String> res = new ArrayList<String>();
		
		int i=0;
		for (CWResult protein : collection){
			i++;
			monitor.subTask("Protein (" + i + "/" + 
					collection.size() + "): " + protein.getName());
			monitor.worked(1);
			if (monitor.isCanceled())
				return null;

			String cwikiURI = "http://www.conceptwiki.org/concept/" +protein.getCwid();

			List<LDAInfo> targetInfo = OPSLDAJava.GetTargetInfo(cwikiURI, getOPSLDAendpoint());
			String tinfo = protein.getName() + "\n";
			for (LDAInfo info : targetInfo){
				tinfo=tinfo + "\n" + info.field + "=" + info.value;
			}
			res.add(tinfo);
			
		}
		
		return res;
	}


	/**
	 * Look up compound information, such as pharmacological props for a set 
	 * of CWResults by CWID.
	 * @param collection
	 * @param monitor
	 * @return List of molecules with properties set
	 * @throws BioclipseException
	 */
	public List<IMolecule> getCompoundsInfo(List<CWResult> collection, IProgressMonitor monitor) 
			throws BioclipseException{
		
		ICDKManager cdk = net.bioclipse.cdk.business.Activator.getDefault().getJavaCDKManager();
		java.util.List<IMolecule> mols = new ArrayList<IMolecule>();

		int i=0;
		monitor.beginTask("Retrieving information about compounds from Open PHACTS", collection.size()*2);
		for (CWResult compound : collection){
			i++;
			//Concatenate into URL and look up in OPS
			String cwikiCompound="http://www.conceptwiki.org/concept/" + compound.getCwid();

			//Look up compound info
			//======================
			logger.debug("Looking up info for compound: " + compound);
			monitor.subTask("Looking up compound info for compound (" + i + "/" + 
					collection.size() + "): " + compound.getName());
			monitor.worked(1);
			if (monitor.isCanceled())
				return null;

			List<LDAInfo> compoundInfo=OPSLDAJava.GetCompoundInfo(cwikiCompound,getOPSLDAendpoint());
			
			//First we
			Map<String, String> props = new HashMap<String, String>();
			ICDKMolecule cdkmol=null;
			
			for (LDAInfo info : compoundInfo){

				props.put(info.field, info.value);
				logger.debug("Added compound info:" + info.source +" - " + info.field + " - " + info.value);
				
				//Treat the chemspider field SMILES as special
				if (info.source.equals("http://www.chemspider.com") && info.field.equals("smiles")){
					String smiles = info.value;
					cdkmol = cdk.fromSMILES(smiles);
				}
			}
			
			//Look up pharmacology info
			//======================
			logger.debug("Looking up pharmacology for compound: " + compound);
			monitor.subTask("Looking up compound info for compound (" + i + "/" + 
					collection.size() + "): " + compound.getName());
			monitor.worked(1);
			if (monitor.isCanceled())
				return null;
			List<Map<String,String>> pharm2 = OPSLDAJava.GetPharmacologyByCompound(cwikiCompound,getOPSLDAendpoint());
			for(Map<String,String> pharmainfo : pharm2)
			{
				for(Entry<String,String> entry: pharmainfo.entrySet()){
					props.put(entry.getKey(), entry.getValue());
					logger.debug("Added Pharmacology Field: "+ entry.getKey() + " Value: "+entry.getValue());
				}
			}
			
			for (String key : props.keySet())
				cdkmol.setProperty(key, props.get(key));

			mols.add(cdkmol);

		}
			
		return mols;

	}

}
