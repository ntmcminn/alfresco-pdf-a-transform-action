package org.alfresco.extension.pdfarchival.repo.action.executer;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.enterprise.repo.content.JodConverter;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.extension.pdfarchival.constraints.MapConstraint;
import org.alfresco.extension.pdfarchival.model.PDFArchiveModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.artofsolving.jodconverter.document.DefaultDocumentFormatRegistry;
import org.artofsolving.jodconverter.document.DocumentFamily;
import org.artofsolving.jodconverter.document.DocumentFormat;
import org.artofsolving.jodconverter.document.DocumentFormatRegistry;

public class PDFConvertToArchivableActionExecuter extends ActionExecuterAbstractBase
{
	
private boolean createNew = true;
	
    protected static final String 				FILE_EXTENSION 		= ".pdf";
    protected static final String 				FILE_MIMETYPE  		= "application/pdf";
    protected static final String				PDF 				= "pdf";
    
    protected ServiceRegistry     				serviceRegistry;
    //Default number of map entries at creation 
    protected static final int 					INITIAL_OPTIONS 	= 5;
    public static final String                  PARAM_INPLACE    	= "inplace";

	/**
     * The logger
     */
    private static Log         logger                   				  = LogFactory.getLog(PDFConvertToArchivableActionExecuter.class);

    /**
     * Action constants
     */
    public static final String NAME                     				  = "pdf-archive";
    public static final String PARAM_DESTINATION_FOLDER 				  = "destination-folder";
    public static final String PARAM_DESTINATION_NAME					  = "destination-name";
    public static final String PARAM_ARCHIVE_LEVEL						  = "archive-level";

    /**
     * Constraints
     */
    public static HashMap<String, String> archiveLevelConstraint          = new HashMap<String, String>();
    
    private final String PDFA											  = "PDF/A";
    
    private JodConverter jodConverter;
    
    /**
     * Add parameter definitions
     */
    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_FOLDER, DataTypeDefinition.NODE_REF, false, getParamDisplayLabel(PARAM_DESTINATION_FOLDER)));
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_NAME, DataTypeDefinition.TEXT, false, getParamDisplayLabel(PARAM_DESTINATION_NAME)));
        paramList.add(new ParameterDefinitionImpl(PARAM_ARCHIVE_LEVEL, DataTypeDefinition.INT, true, getParamDisplayLabel(PARAM_ARCHIVE_LEVEL), false, "pdfc-archivelevel"));
        paramList.add(new ParameterDefinitionImpl(PARAM_INPLACE, DataTypeDefinition.BOOLEAN, false, getParamDisplayLabel(PARAM_INPLACE), false));
    }
    
	@Override
	protected void executeImpl(Action action, NodeRef actionedUponNodeRef) 
	{
		
		//is the JOD Converter available?  If not, punch out right away.
		if(!jodConverter.isAvailable()) throw new AlfrescoRuntimeException("JOD Converter is not available");
		
		NodeService ns = serviceRegistry.getNodeService();
		ContentService cs = serviceRegistry.getContentService();
		
		if(!ns.exists(actionedUponNodeRef))
		{
			throw new AlfrescoRuntimeException("PDF/A convert action called on non-existent node: " + actionedUponNodeRef);
		}
		
        Boolean inplace = Boolean.valueOf(String.valueOf(action.getParameterValue(PARAM_INPLACE)));
        Integer archiveLevel = Integer.valueOf(String.valueOf(action.getParameterValue(PARAM_ARCHIVE_LEVEL)));
        String providedName = String.valueOf(action.getParameterValue(PARAM_DESTINATION_NAME));
        
		// get an output file for the new PDF (temp file)
        File out = getTempFile(actionedUponNodeRef);
                   
        // copy the source node content to a temp file
        File in = nodeRefToTempFile(actionedUponNodeRef);
        
		// transform to PDF/A
        OfficeDocumentConverter converter=new OfficeDocumentConverter(jodConverter.getOfficeManager());
        
        //DocumentFormatRegistry formatRegistry = new DefaultDocumentFormatRegistry();
        //formatRegistry.getFormatByExtension(PDF).setInputFamily(DocumentFamily.DRAWING);
        //OfficeDocumentConverter converter = new OfficeDocumentConverter(jodConverter.getOfficeManager(), formatRegistry);
        
        try
        {
        	converter.convert(in, out, getDocumentFormat(archiveLevel));
        }
        catch(Exception ex)
        {
        	ex.printStackTrace();
        }
		
		NodeRef destinationNode = createDestinationNode(getFilename(providedName, actionedUponNodeRef), 
        		(NodeRef)action.getParameterValue(PARAM_DESTINATION_FOLDER), actionedUponNodeRef, inplace);
        ContentWriter writer = serviceRegistry.getContentService().getWriter(destinationNode, ContentModel.PROP_CONTENT, true);
        writer.setEncoding(cs.getReader(actionedUponNodeRef, ContentModel.PROP_CONTENT).getEncoding());
        writer.setMimetype(FILE_MIMETYPE);
        writer.putContent(out);

        // apply the marker aspect
        ns.addAspect(destinationNode, PDFArchiveModel.ASPECT_ARCHIVAL, new HashMap<QName, Serializable>());
        // delete the temp files
        in.delete();
        out.delete();

	}

	/**
	* Returns a DocumentFormat that will output to PDF/A
	*/
	private DocumentFormat getDocumentFormat(int level) {

		DocumentFormat format = new DocumentFormat(PDFA, PDF, FILE_MIMETYPE);
	    Map<String, Object> properties = new HashMap<String, Object>();
	    properties.put("FilterName", "draw_pdf_Export");

	    Map<String, Object> filterData = new HashMap<String, Object>();
	    filterData.put("SelectPdfVersion", level);
	    properties.put("FilterData", filterData);

	    format.setInputFamily(DocumentFamily.DRAWING);
	    format.setLoadProperties(properties);
	    format.setStoreProperties(DocumentFamily.DRAWING, properties);

	    return format;
	}
	
	private String getFilename(String providedName, NodeRef targetNodeRef)
    {
		NodeService ns = serviceRegistry.getNodeService();
		
        String fileName = null;
        if(providedName != null)
        {
        	fileName = String.valueOf(providedName);
        	if(!fileName.endsWith(FILE_EXTENSION))
        	{
        		fileName = fileName + FILE_EXTENSION;
        	}
        }
        else
        {
        	fileName = String.valueOf(ns.getProperty(targetNodeRef, ContentModel.PROP_NAME));
        }
        return fileName;
    }
	
    /**
     * @param actionedUponNodeRef
     * @return
     */
    protected ContentReader getReader(NodeRef nodeRef)
    {
        // First check that the node is a sub-type of content
        QName typeQName = serviceRegistry.getNodeService().getType(nodeRef);
        if (serviceRegistry.getDictionaryService().isSubClass(typeQName, ContentModel.TYPE_CONTENT) == false)
        {
            // it is not content, so can't transform
            return null;
        }

        // Get the content reader
        ContentReader contentReader = serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);

        return contentReader;
    }

    /**
     * @param ruleAction
     * @param filename
     * @return
     */
    protected NodeRef createDestinationNode(String filename, NodeRef destinationParent, NodeRef target, boolean inplace)
    {

    	NodeRef destinationNode;
    	
    	// if inplace mode is turned on, the destination for the modified content
    	// is the original node
    	if(inplace)
    	{
    		return target;
    	}
    	
    	if(createNew)
    	{
	    	//create a file in the right location
	        FileInfo fileInfo = serviceRegistry.getFileFolderService().create(destinationParent, filename, ContentModel.TYPE_CONTENT);
	        destinationNode = fileInfo.getNodeRef();
    	}
    	else
    	{
    		try 
    		{
	    		FileInfo fileInfo = serviceRegistry.getFileFolderService().copy(target, destinationParent, filename);
	    		destinationNode = fileInfo.getNodeRef();
    		}
    		catch(FileNotFoundException fnf)
    		{
    			throw new AlfrescoRuntimeException(fnf.getMessage(), fnf);
    		}
    	}

        return destinationNode;
    }
    
    protected int getInteger(Serializable val)
    {
    	if(val == null)
    	{ 
    		return 0;
    	}
    	try
    	{
    		return Integer.parseInt(val.toString());
    	}
    	catch(NumberFormatException nfe)
    	{
    		return 0;
    	}
    }
    
    protected File getTempFile(NodeRef nodeRef)
    {
    	File alfTempDir = TempFileProvider.getTempDir();
        File toolkitTempDir = new File(alfTempDir.getPath() + File.separatorChar + nodeRef.getId());
        toolkitTempDir.mkdir();
        File file = new File(toolkitTempDir, serviceRegistry.getFileFolderService().getFileInfo(nodeRef).getName());
        
        return file;
    }
    
    protected File nodeRefToTempFile(NodeRef nodeRef)
    {
    	ContentService cs = serviceRegistry.getContentService();
        File tempFromFile = TempFileProvider.createTempFile("PDFAConverter-", nodeRef.getId()
                + FILE_EXTENSION);
        ContentReader reader = cs.getReader(nodeRef, ContentModel.PROP_CONTENT);
        reader.getContent(tempFromFile);
        return tempFromFile;
    }
    
    /**
     * Set a service registry to use, this will do away with all of the
     * individual service registrations
     * 
     * @param serviceRegistry
     */
    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Sets whether a PDF action creates a new empty node or copies the source node, preserving
     * the content type, applied aspects and properties
     * 
     * @param createNew
     */
    public void setCreateNew(boolean createNew)
    {
    	this.createNew = createNew;
    }
    
    /**
     * Setter for constraint bean
     * 
     * @param encryptionLevelConstraint
     */
    public void setArchiveLevelConstraint(MapConstraint mc)
    {
        archiveLevelConstraint.putAll(mc.getAllowableValues());
    }

    public void setJodConverter(JodConverter jodConverter)
    {
    	this.jodConverter = jodConverter;
    }
}
