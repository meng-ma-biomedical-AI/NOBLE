package edu.pitt.dbmi.nlp.noble.eval.ehost;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.pitt.dbmi.nlp.noble.mentions.model.DomainOntology;
import edu.pitt.dbmi.nlp.noble.ontology.IClass;
import edu.pitt.dbmi.nlp.noble.ontology.IInstance;
import edu.pitt.dbmi.nlp.noble.ontology.IOntology;
import edu.pitt.dbmi.nlp.noble.ontology.IProperty;
import edu.pitt.dbmi.nlp.noble.ontology.OntologyUtils;
import edu.pitt.dbmi.nlp.noble.ontology.owl.OOntology;
import edu.pitt.dbmi.nlp.noble.tools.TextTools;
import edu.pitt.dbmi.nlp.noble.util.XMLUtils;

public class EhostToInstances {

	public static void main(String[] args) throws Exception {
		File inputDir = new File("/home/tseytlin/Data/NobleMentions/Output/Risk/2017-05-11 10.27.52/eHOST");
		File ontologyFile = new File("/home/tseytlin/Data/NobleMentions/Gold/HeartDiseaseRiskFactors/heartDiseaseInDiabetics.owl");
		File instanceFile = new File("/home/tseytlin/Data/NobleMentions/Output/Risk/heartDiseaseInDiabeticsInstances.owl");
		
		
		EhostToInstances i2e = new EhostToInstances();
		System.out.println("conver: "+ontologyFile.getAbsolutePath()+" ..");
		i2e.convert(inputDir,ontologyFile,instanceFile);
		
		System.out.println("ok");


	}
	/**
	 * convert eHOST directory to instances 
	 * @param inputDir - eHOST directory
	 * @param ontologyFile - parent ontology
	 * @param instanceFile - instance ontolgoy
	 */
	public void convert(File inputDir, File ontologyFile, File instanceFile) throws Exception {
		File saved = new File(inputDir,InstancesToEhost.SAVED_DIR);
		if(!saved.exists()){
			throw new Exception("Directory "+saved.getAbsolutePath()+" doesn't exist ");
		}
		
		// initialize instances ontology
		URI uri = OntologyUtils.createOntologyInstanceURI(ontologyFile.getAbsolutePath());
		IOntology ontology = OOntology.createOntology(uri,ontologyFile);
		
		for(File file: saved.listFiles()){
			if(file.getName().endsWith(InstancesToEhost.KNOWTATOR_SUFFIX)){
				convertComposition(file,ontology);
			}
		}
		
		// write output ontolgoy
		ontology.write(new FileOutputStream(instanceFile),IOntology.OWL_FORMAT);
		
	}
	
	/**
	 * convert composition 
	 * @param file - saved annotation file
	 * @param ontology - target ontology
	 */
	private void convertComposition(File file, IOntology ontology) throws Exception {
		Document doc = XMLUtils.parseXML(new FileInputStream(file));
		Element root = doc.getDocumentElement();
		String title = root.getAttribute("textSource");
		
		// create an instance
		IInstance composition = ontology.getClass(DomainOntology.COMPOSITION).createInstance(OntologyUtils.toResourceName(title));
		
		// add title
		composition.addPropertyValue(ontology.getProperty(DomainOntology.HAS_TITLE),title);
		
		// load annotations
		Map<String,Element> annotationMap = loadElements(root,"annotation","mention");
		Map<String,Element> slotMap = loadElements(root,"stringSlotMention",null);
		Map<String,Element> classMap = loadElements(root,"classMention",null);
		
		// init properties
		IProperty hasAnnotationType = ontology.getProperty(DomainOntology.HAS_ANNOTATION_TYPE);
		IProperty hasAnnotationText = ontology.getProperty(DomainOntology.HAS_ANNOTATION_TEXT);
		IProperty hasSpan = ontology.getProperty(DomainOntology.HAS_SPAN);
		IProperty hasMentionAnnotation = ontology.getProperty(DomainOntology.HAS_MENTION_ANNOTATION);
		
		
		// go through classes
		for(String classID : classMap.keySet()){
			Element classE = classMap.get(classID);
			IClass mentionClass = ontology.getClass(getElementId(classE,"mentionClass"));
			if(mentionClass != null){
				// create an instance
				IInstance var = mentionClass.createInstance(classID);
				
				// add type
				IInstance aType = DomainOntology.getDefaultInstance(ontology.getClass(DomainOntology.ANNOTATION_MENTION));
				var.addPropertyValue(hasAnnotationType,aType);
				
				// add spans and text
				Element annotation = annotationMap.get(classID);
				if(annotation != null){
					for(Element e: XMLUtils.getChildElements(annotation,"spannedText")){
						var.addPropertyValue(hasAnnotationText,e.getTextContent().trim());
					}	
					for(Element e: XMLUtils.getChildElements(annotation,"span")){
						String span = e.getAttribute("start")+":"+e.getAttribute("end");
						var.addPropertyValue(hasSpan,span);
					}
				}
				
				// now add attributes
				for(Element e: XMLUtils.getChildElements(classE,"hasSlotMention")){
					Element slotE = slotMap.get(e.getAttribute("id"));
					if(slotE != null){
						String prop  = getElementId(slotE,"mentionSlot");
						String value = XMLUtils.getElementByTagName(slotE, "stringSlotMentionValue").getAttribute("value");
						
						IProperty property = ontology.getProperty(prop);
						IClass valueClass = ontology.getClass(value);
						
						if(property != null && valueClass != null){
							IInstance valueInst = DomainOntology.getDefaultInstance(valueClass);
							var.addPropertyValue(property,valueInst);
						}
					}
				}
				
				
				// add to composition
				composition.addPropertyValue(hasMentionAnnotation, var);
				
			}
		}
		
	}
	
	
	
	
	private String getElementId(Element parent, String tag){
		Element e = XMLUtils.getElementByTagName(parent,tag);
		return e != null? e.getAttribute("id"):null;
	}
	
	/**
	 * create mapping of elements
	 * @param root
	 * @param string
	 * @param string2
	 * @return
	 */
	private Map<String, Element> loadElements(Element root, String tag, String idTag) {
		Map<String,Element> map = new LinkedHashMap<String,Element>();
		for(Element e: XMLUtils.getChildElements(root, tag)){
			String id = e.getAttribute("id");
			if(id == null || id.length() == 0){
				id = getElementId(e, idTag);
			}
			map.put(id,e);
		}
		return map;
	}

}