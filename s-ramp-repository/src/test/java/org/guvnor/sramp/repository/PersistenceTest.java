package org.guvnor.sramp.repository;

import java.io.InputStream;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;
import org.s_ramp.xmlns._2010.s_ramp.XmlDocument;
import org.s_ramp.xmlns._2010.s_ramp.XsdDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:kurt.stam@gmail.com">Kurt Stam</a>
 */
public class PersistenceTest {
    
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private static PersistenceManager persistenceManager = null;
    
    @BeforeClass
    public static void setup() {
        persistenceManager = PersistenceFactory.newInstance();
    }
    
    @Test
    public void testSavePO_XSD() throws Exception {
        
        String artifactFileName = "PO.xsd";
        String type = "xsd";
        InputStream POXsd = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
        
        String identifier = persistenceManager.persistArtifact(artifactFileName, type, POXsd);
        POXsd.close();
        log.info("persisted PO.xsd to JCR, returned ID=" + identifier);
        Assert.assertNotNull(identifier);
        DerivedArtifacts derivedArtifactsManager = DerivedArtifactsFactory.newInstance();
        XsdDocument xsdDocument = derivedArtifactsManager.createDerivedArtifact(XsdDocument.class, identifier);
        String derivedIdentifier = persistenceManager.persistDerivedArtifact(xsdDocument);
        
        //print out the derived node
        persistenceManager.printArtifactGraph(derivedIdentifier);
        
        Assert.assertEquals(new Long(2376l), xsdDocument.getContentSize());
        System.out.println("XsdDocument = " + xsdDocument);
    }
    
    /**
     * For now expecting a DerivedArtifactsCreationException since the ModeShape
     * Sequencer for XML is not yet up deriving all the necessary artifacts.
     * @throws Exception
     */
    @Test(expected=DerivedArtifactsCreationException.class)
    public void testSavePO_XML() throws Exception {
        
        String artifactFileName = "PO.xml";
        String type = "xml";
        InputStream POXml = this.getClass().getResourceAsStream("/sample-files/xml/" + artifactFileName);
        
        String identifier = persistenceManager.persistArtifact(artifactFileName, type, POXml);
        POXml.close();
        log.info("persisted PO.xml to JCR, returned ID=" + identifier);
        Assert.assertNotNull(identifier);
        DerivedArtifacts derivedArtifactsManager = DerivedArtifactsFactory.newInstance();
        XmlDocument xmlDocument = derivedArtifactsManager.createDerivedArtifact(XmlDocument.class, identifier);
        String derivedIdentifier = persistenceManager.persistDerivedArtifact(xmlDocument);
        
      //print out the derived node
        persistenceManager.printArtifactGraph(derivedIdentifier);
        
        Assert.assertEquals(new Long(2376l), xmlDocument.getContentSize());
        
        System.out.println("XmlDocument = " + xmlDocument);
    }
}
