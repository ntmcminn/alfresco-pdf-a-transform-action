package org.alfresco.extension.pdfarchival.model;

import org.alfresco.service.namespace.QName;

public interface PDFArchiveModel 
{
	//namespace
	static final String PDFTOOLKIT_MODEL_1_0_URI = "http://www.alfresco.com/model/pdfarchive/1.0";
	
	//marker aspect for archival PDFs
	static final QName ASPECT_ARCHIVAL = QName.createQName(PDFTOOLKIT_MODEL_1_0_URI, "archival");
}
