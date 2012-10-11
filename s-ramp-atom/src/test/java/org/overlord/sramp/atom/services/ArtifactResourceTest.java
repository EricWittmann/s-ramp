/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.sramp.atom.services;

import static org.jboss.resteasy.test.TestPortProvider.generateURL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.xml.namespace.QName;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.MultipartConstants;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.junit.Assert;
import org.junit.Test;
import org.overlord.sramp.SrampConstants;
import org.overlord.sramp.SrampModelUtils;
import org.overlord.sramp.atom.MediaType;
import org.overlord.sramp.atom.SrampAtomUtils;
import org.overlord.sramp.atom.client.ClientRequest;
import org.overlord.sramp.atom.err.SrampAtomException;
import org.overlord.sramp.atom.providers.SrampAtomExceptionProvider;
import org.s_ramp.xmlns._2010.s_ramp.Artifact;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactEnum;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.Document;
import org.s_ramp.xmlns._2010.s_ramp.Message;
import org.s_ramp.xmlns._2010.s_ramp.UserDefinedArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.WsdlDocument;
import org.s_ramp.xmlns._2010.s_ramp.XmlDocument;
import org.s_ramp.xmlns._2010.s_ramp.XsdDocument;

import test.org.overlord.sramp.atom.TestUtils;

/**
 * Test of the jax-rs resource that handles Artifacts.
 *
 * @author eric.wittmann@redhat.com
 */
public class ArtifactResourceTest extends AbstractResourceTest {

    String uuid = null;

	/**
	 * @throws Exception
	 */
	@Test
	public void testDerivedArtifactCreate() throws Exception {
		// Making a client call to the actual XsdDocument implementation running in
		// an embedded container.
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/ElementDeclaration"));

		// read the XsdDocument from file
		String artifactFileName = "PO.xsd";
		InputStream POXsd = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
		String xmltext = TestUtils.convertStreamToString(POXsd);
		POXsd.close();

		request.header("Slug", artifactFileName);
		request.body(MediaType.APPLICATION_XML, xmltext);

		try {
			request.post(String.class);
			Assert.fail("Expected an error here.");
		} catch (SrampAtomException e) {
			Assert.assertEquals("Failed to create artifact because 'ElementDeclaration' is a derived type.", e.getMessage());
			String stack = SrampAtomExceptionProvider.getRootStackTrace(e);
			Assert.assertTrue(stack.contains("org.overlord.sramp.atom.services.ArtifactResource.create"));
		}
	}

	/**
	 * Tests adding a PDF document.
	 * @throws Exception
	 */
	@Test
	public void testPDFDocument() throws Exception {
		// Add the PDF to the repository
		String artifactFileName = "sample.pdf";
		InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/core/" + artifactFileName);
		//String uuid = null;
		try {
			ClientRequest request = new ClientRequest(generateURL("/s-ramp/core/Document"));
			request.header("Slug", artifactFileName);
			request.body("application/pdf", contentStream);

			ClientResponse<Entry> response = request.post(Entry.class);

			Entry entry = response.getEntity();
			Assert.assertEquals(artifactFileName, entry.getTitle());
			BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
			Assert.assertTrue(arty instanceof Document);
			Document doc = (Document) arty;
			Assert.assertEquals(artifactFileName, doc.getName());
			Assert.assertEquals(Long.valueOf(218882), doc.getContentSize());
			Assert.assertEquals("application/pdf", doc.getContentType());
			uuid = doc.getUuid();
		} finally {
			IOUtils.closeQuietly(contentStream);
		}

		// Make sure we can query it now
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/core/Document/" + uuid));
		ClientResponse<Entry> response = request.get(Entry.class);

		Entry entry = response.getEntity();
		BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
		Assert.assertTrue(arty instanceof Document);
		Document doc = (Document) arty;
		Assert.assertEquals(artifactFileName, doc.getName());
		Assert.assertEquals(Long.valueOf(218882), doc.getContentSize());
		Assert.assertEquals("sample.pdf", doc.getName());
		Assert.assertEquals("application/pdf", doc.getContentType());
        //Obtain the content for visual inspection
        ClientRequest request2 = new ClientRequest(generateURL("/s-ramp/core/Document/" + uuid + "/media"));
        ClientResponse<InputStream> response2 = request2.get(InputStream.class);
        if (response2.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                + response.getStatus());
        }
        InputStream in = response2.getEntity();
        File file = new File("target/SRAMP-sample.pdf");
        OutputStream out = new FileOutputStream(file);
        IOUtils.copy(in, out);
        out.flush();
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(out);
	}

	/**
     * Tests adding a BRMS Pkg document.
     * @throws Exception
     */
    @Test
    public void testBrmsPkgDocument() throws Exception {
        // Add the pkg to the repository
        String artifactFileName = "defaultPackage.pkg";
        InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/user/" + artifactFileName);
        String uuid = null;
        try {
            ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/BrmsPkgDocument"));
            request.header("Slug", artifactFileName);
            request.body("application/octet-stream", contentStream);

            ClientResponse<Entry> response = request.post(Entry.class);

            Entry entry = response.getEntity();
            Assert.assertEquals(artifactFileName, entry.getTitle());
            BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
            Assert.assertTrue(arty instanceof UserDefinedArtifactType);
            UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
            Assert.assertEquals(artifactFileName, doc.getName());
            Assert.assertEquals("BrmsPkgDocument", doc.getUserType());
            Assert.assertEquals(Long.valueOf(17043), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
            Assert.assertEquals("application/octet-stream", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));
            uuid = doc.getUuid();
        } finally {
            IOUtils.closeQuietly(contentStream);
        }

        // Make sure we can query it now
        ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/BrmsPkgDocument/" + uuid));
        ClientResponse<Entry> response = request.get(Entry.class);

        Entry entry = response.getEntity();
        BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
        Assert.assertTrue(arty instanceof UserDefinedArtifactType);
        UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
        Assert.assertEquals(artifactFileName, doc.getName());
        Assert.assertEquals(Long.valueOf(17043), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
        Assert.assertEquals("defaultPackage.pkg", doc.getName());
        Assert.assertEquals("application/octet-stream", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));
    }

    /**
     * Tests adding a JPG document.
     * @throws Exception
     */
    @Test
    public void testJPGDocument() throws Exception {
        // Add the jpg to the repository
        String artifactFileName = "photo.jpg";
        InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/user/" + artifactFileName);
        String uuid = null;
        try {
            ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/JpgDocument"));
            request.header("Slug", artifactFileName);
            request.body("application/octet-stream", contentStream);

            ClientResponse<Entry> response = request.post(Entry.class);

            Entry entry = response.getEntity();
            Assert.assertEquals(artifactFileName, entry.getTitle());
            BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
            Assert.assertTrue(arty instanceof UserDefinedArtifactType);
            UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
            Assert.assertEquals(artifactFileName, doc.getName());
            Assert.assertEquals("JpgDocument", doc.getUserType());
            Assert.assertEquals(Long.valueOf(2966447), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
            Assert.assertEquals("application/octet-stream", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));
            uuid = doc.getUuid();
        } finally {
            IOUtils.closeQuietly(contentStream);
        }

        // Make sure we can query it now
        ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/JpgDocument/" + uuid));
        ClientResponse<Entry> response = request.get(Entry.class);

        Entry entry = response.getEntity();
        BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
        Assert.assertTrue(arty instanceof UserDefinedArtifactType);
        UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
        Assert.assertEquals(artifactFileName, doc.getName());
        Assert.assertEquals(Long.valueOf(2966447), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
        Assert.assertEquals("photo.jpg", doc.getName());
        Assert.assertEquals("application/octet-stream", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));

        //Obtain the content for visual inspection
        ClientRequest request2 = new ClientRequest(generateURL("/s-ramp/user/JpgDocument/" + uuid + "/media"));
        ClientResponse<InputStream> response2 = request2.get(InputStream.class);
        if (response2.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                + response.getStatus());
        }
        InputStream in = response2.getEntity();
        File file = new File("target/SRAMP-photo.jpg");
        OutputStream out = new FileOutputStream(file);
        IOUtils.copy(in, out);
        out.flush();
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(out);
    }

	/**
     * Tests adding a BPMN Process Definition document.
     * @throws Exception
     */
    @Test
    public void testBpmnUserDefinedDocumentCreate() throws Exception {
        // Add the BPMN process to the repository
        String artifactFileName = "Evaluation.bpmn";
        InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/user/" + artifactFileName);
        String uuid = null;
        try {
            ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/BpmnDocument"));
            request.header("Slug", artifactFileName);
            request.body("application/xml", contentStream);

            ClientResponse<Entry> response = request.post(Entry.class);

            Entry entry = response.getEntity();
            Assert.assertEquals(artifactFileName, entry.getTitle());
            BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
            Assert.assertTrue(arty instanceof UserDefinedArtifactType);
            UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
            Assert.assertEquals(artifactFileName, doc.getName());
            Assert.assertEquals("BpmnDocument", doc.getUserType());
            Assert.assertEquals(Long.valueOf(12482), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
            Assert.assertEquals("application/xml", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));
            uuid = doc.getUuid();
        } finally {
            IOUtils.closeQuietly(contentStream);
        }

        // Make sure we can query it now
        ClientRequest request = new ClientRequest(generateURL("/s-ramp/user/BpmnDocument/" + uuid));
        ClientResponse<Entry> response = request.get(Entry.class);

        Entry entry = response.getEntity();
        BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
        Assert.assertTrue(arty instanceof UserDefinedArtifactType);
        UserDefinedArtifactType doc = (UserDefinedArtifactType) arty;
        Assert.assertEquals(artifactFileName, doc.getName());
        Assert.assertEquals(Long.valueOf(12482), Long.valueOf(doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_SIZE))));
        Assert.assertEquals("Evaluation.bpmn", doc.getName());
        Assert.assertEquals("application/xml", doc.getOtherAttributes().get(new QName(SrampConstants.SRAMP_CONTENT_TYPE)));

        ClientResponse<String> content = request.get(String.class);
        System.out.println("Content=" + content.getEntity());
    }

	/**
	 * Tests adding a wsdl document.
	 * @throws Exception
	 */
	@Test
	public void testWsdlDocumentCreate() throws Exception {
		// Add the PDF to the repository
		String artifactFileName = "sample.wsdl";
		InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/wsdl/" + artifactFileName);
		String uuid = null;
		try {
			ClientRequest request = new ClientRequest(generateURL("/s-ramp/wsdl/WsdlDocument"));
			request.header("Slug", artifactFileName);
			request.body("application/xml", contentStream);

			ClientResponse<Entry> response = request.post(Entry.class);

			Entry entry = response.getEntity();
			Assert.assertEquals(artifactFileName, entry.getTitle());
			BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
			Assert.assertTrue(arty instanceof WsdlDocument);
			WsdlDocument doc = (WsdlDocument) arty;
			Assert.assertEquals(artifactFileName, doc.getName());
			Assert.assertEquals(Long.valueOf(1642), doc.getContentSize());
			Assert.assertEquals("application/xml", doc.getContentType());
			uuid = doc.getUuid();
		} finally {
			IOUtils.closeQuietly(contentStream);
		}

		// Make sure we can query it now
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/wsdl/WsdlDocument/" + uuid));
		ClientResponse<Entry> response = request.get(Entry.class);
		Entry entry = response.getEntity();
		BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
		Assert.assertNotNull(arty);
		Assert.assertTrue(arty instanceof WsdlDocument);
		WsdlDocument wsdlDoc = (WsdlDocument) arty;
		Assert.assertEquals(Long.valueOf(1642), wsdlDoc.getContentSize());
		Assert.assertEquals("sample.wsdl", wsdlDoc.getName());

		// Make sure we can query the derived content
		ClientRequest frequest = new ClientRequest(generateURL("/s-ramp/wsdl/Message"));
		ClientResponse<Feed> fresponse = frequest.get(Feed.class);
		Feed feed = fresponse.getEntity();
		Assert.assertNotNull(feed);
		Assert.assertEquals(2, feed.getEntries().size());
		String findReqMsgUuid = null;
		for (Entry atomEntry : feed.getEntries()) {
			if ("findRequest".equals(atomEntry.getTitle())) {
				findReqMsgUuid = atomEntry.getId().toString();
			}
		}
		Assert.assertNotNull(findReqMsgUuid);

		// Get the full meta data for the derived Message
		request = new ClientRequest(generateURL("/s-ramp/wsdl/Message/" + findReqMsgUuid));
		response = request.get(Entry.class);
		entry = response.getEntity();
		arty = SrampAtomUtils.unwrapSrampArtifact(entry);
		Assert.assertNotNull(arty);
		Assert.assertTrue(arty instanceof Message);
		Message message = (Message) arty;
		Assert.assertEquals("findRequest", message.getNCName());
		Assert.assertEquals("http://ewittman.redhat.com/sample/2012/09/wsdl/sample.wsdl", message.getNamespace());
	}
	
    @Path("s-ramp")
    public static interface MultipartClient
    {
       @Path("xsd/XsdDocument")
       @POST
       @Consumes(MultipartConstants.MULTIPART_RELATED)
       public void postRelated(MultipartRelatedOutput output);
    }
	
	@Test
	public void testMultiPartCreate() {
	    try {
	        ClientRequest request = new ClientRequest(generateURL("/s-ramp/core/XmlDocument"));

	        MultipartRelatedOutput output = new MultipartRelatedOutput();
	        
	        XmlDocument xmlDocument = new XmlDocument();
	        xmlDocument.setArtifactType(BaseArtifactEnum.XML_DOCUMENT);
	        xmlDocument.setCreatedBy("kurt");
	        xmlDocument.setDescription("In depth description of this XML document");
	        xmlDocument.setName("PO.xml");
	        xmlDocument.setUuid("my-uuid");
	        xmlDocument.setVersion("1.0");
	        
	        Entry atomEntry = new Entry();
	        Artifact arty = new Artifact();
	        arty.setXmlDocument(xmlDocument);
	        atomEntry.setAnyOtherJAXBObject(arty);

	        MediaType mediaType = new MediaType("application", "atom+xml");
	        output.addPart(atomEntry, mediaType);
	        
	        String artifactFileName = "PO.xml";
	        InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/core/" + artifactFileName);
	        MediaType mediaType2 = new MediaType("application", "xml");
	        output.addPart(contentStream, mediaType2);
	        
	        request.body(MultipartConstants.MULTIPART_RELATED, output);
    
            ClientResponse<Entry> response = request.post(Entry.class);
//    
            Entry entry = response.getEntity();
            Assert.assertEquals(artifactFileName, entry.getTitle());
            Artifact artifact = entry.getAnyOtherJAXBObject(Artifact.class);
            Assert.assertEquals("my-uuid",artifact.getXmlDocument().getUuid());
            Assert.assertEquals(Long.valueOf(825), artifact.getXmlDocument().getContentSize());
            Assert.assertEquals(artifactFileName, artifact.getXmlDocument().getName());
	    } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
	}

	/**
	 * Tests adding an artifact without a slug.
	 * @throws Exception
	 */
	@Test
	public void testCreateNoSlug() throws Exception {
		// Add the PDF to the repository
		String artifactFileName = "sample.wsdl";
		InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/wsdl/" + artifactFileName);
		try {
			ClientRequest request = new ClientRequest(generateURL("/s-ramp/wsdl/WsdlDocument"));
			request.body("application/xml", contentStream);

			ClientResponse<Entry> response = request.post(Entry.class);

			Entry entry = response.getEntity();
			Assert.assertEquals("newartifact.wsdl", entry.getTitle());
			BaseArtifactType arty = SrampAtomUtils.unwrapSrampArtifact(entry);
			Assert.assertTrue(arty instanceof WsdlDocument);
			WsdlDocument doc = (WsdlDocument) arty;
			Assert.assertEquals("newartifact.wsdl", doc.getName());
		} finally {
			IOUtils.closeQuietly(contentStream);
		}
	}

	/**
	 * Tests that artifact derivation is happening.
	 * @throws Exception
	 */
	@Test
	public void testArtifactDerivation() throws Exception {
		// Add the PDF to the repository
		String artifactFileName = "PO.xsd";
		InputStream contentStream = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
		try {
			ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument"));
			request.body("application/xml", contentStream);
			ClientResponse<Entry> response = request.post(Entry.class);
			response.getEntity();
		} finally {
			IOUtils.closeQuietly(contentStream);
		}

		// Now let's query for the derived artifacts
        ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/ElementDeclaration"));
        ClientResponse<Feed> response = request.get(Feed.class);
        Feed feed = response.getEntity();
        Assert.assertEquals(2, feed.getEntries().size());
        Map<String, Entry> entryMap = new HashMap<String, Entry>();
        for (Entry entry : feed.getEntries()) {
        	entryMap.put(entry.getTitle(), entry);
        }
        Entry purchaseOrder = entryMap.get("purchaseOrder");
        Assert.assertNotNull(purchaseOrder);
        Entry comment = entryMap.get("comment");
        Assert.assertNotNull(comment);
	}

	/**
	 * Does a full test of all the basic Artifact operations.
	 * @throws Exception
	 */
	@Test
	public void testFullPurchaseOrderXSD() throws Exception {
		// Add
		Entry entry = doAddXsd();
		URI entryId = entry.getId();

		// Get
		entry = doGetXsdEntry(entryId);

		// Get artifact content
		String content = doGetXsdContent(entryId);
		verifyXsdContent(content);

		// Update meta data
		doUpdateXsdEntry(entry);
		entry = doGetXsdEntry(entryId);
		verifyEntryUpdated(entry);

		// Update content
		doUpdateXsdContent(entry);
		content = doGetXsdContent(entryId);
		verifyContentUpdated(content);

		deleteXsdEntry(entryId);
		verifyEntryDeleted(entryId);
	}

	/**
	 * Adds an XSD to the repo by POSTing the content to /s-ramp/xsd/XsdDocument.
	 */
	private Entry doAddXsd() throws Exception {
		// Making a client call to the actual XsdDocument implementation running in
		// an embedded container.
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument"));

		// read the XsdDocument from file
		String artifactFileName = "PO.xsd";
		InputStream POXsd = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
		String xmltext = TestUtils.convertStreamToString(POXsd);
		POXsd.close();

		request.header("Slug", artifactFileName);
		request.body(MediaType.APPLICATION_XML, xmltext);

		ClientResponse<Entry> response = request.post(Entry.class);

		Entry entry = response.getEntity();
		Assert.assertEquals(artifactFileName, entry.getTitle());
		Artifact artifact = entry.getAnyOtherJAXBObject(Artifact.class);
		Assert.assertEquals(Long.valueOf(2376), artifact.getXsdDocument().getContentSize());
		Assert.assertEquals(artifactFileName, artifact.getXsdDocument().getName());

		return entry;
	}

	/**
	 * GETs the Atom entry from the repository (to ensure we have the latest).
	 * @param entryId
	 * @throws Exception
	 */
	private Entry doGetXsdEntry(URI entryId) throws Exception {
		// TODO I think the entryId should be of the format urn:{uuid} and we'll need to parse it - this isn't happening right now though
		String uuid = entryId.toString();

		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid));
		ClientResponse<Entry> response = request.get(Entry.class);

		Entry entry = response.getEntity();
		Artifact artifact = entry.getAnyOtherJAXBObject(Artifact.class);
		Assert.assertNotNull(artifact.getXsdDocument());
		Assert.assertEquals(Long.valueOf(2376), artifact.getXsdDocument().getContentSize());

		return entry;
	}

	/**
	 * Gets the content for the artifact from the repo.
	 * @param entryId
	 * @throws Exception
	 */
	private String doGetXsdContent(URI entryId) throws Exception {
		String uuid = entryId.toString();

		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid + "/media"));
		ClientResponse<String> response = request.get(String.class);

		return response.getEntity();
	}

	/**
	 * Verify that the content returned from the repo is right.
	 * @param content
	 * @throws IOException
	 */
	private void verifyXsdContent(String content) throws IOException {
		Assert.assertNotNull(content);

		String artifactFileName = "PO.xsd";
		InputStream POXsd = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
		try {
			String expectedContent = TestUtils.convertStreamToString(POXsd);
			Assert.assertEquals(expectedContent, content);
		} finally {
			POXsd.close();
		}
	}

	/**
	 * PUTs the Atom entry back into the repository (after making some changes).
	 * @param entry
	 * @throws Exception
	 */
	private void doUpdateXsdEntry(Entry entry) throws Exception {
		// First, make a change to the entry.
		XsdDocument xsdDocument = (XsdDocument) SrampAtomUtils.unwrapSrampArtifact(entry);
		String uuid = xsdDocument.getUuid();
		xsdDocument.setDescription("** Updated description! **");
		SrampModelUtils.setCustomProperty(xsdDocument, "my.property", "Hello World");
		SrampModelUtils.addGenericRelationship(xsdDocument, "NoTargetRel", null);

		Artifact arty = new Artifact();
		arty.setXsdDocument(xsdDocument);
		entry.setAnyOtherJAXBObject(arty);

		// Now PUT the changed entry into the repo
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid));
		request.body(MediaType.APPLICATION_ATOM_XML_ENTRY, entry);
		request.put(Void.class);
	}

	/**
	 * Verifies that the entry has been updated, by checking that the s-ramp extended
	 * Atom entry returned contains the classification and custom property set during
	 * the update phase of the test.
	 * @param entry
	 */
	private void verifyEntryUpdated(Entry entry) throws Exception {
		Artifact srampArtifactWrapper = entry.getAnyOtherJAXBObject(Artifact.class);
		XsdDocument xsdDocument = srampArtifactWrapper.getXsdDocument();
		Assert.assertEquals("** Updated description! **", xsdDocument.getDescription());
		Assert.assertEquals("Hello World", SrampModelUtils.getCustomProperty(xsdDocument, "my.property"));
		Assert.assertNull(SrampModelUtils.getCustomProperty(xsdDocument, "my.missing.property"));
		Assert.assertNotNull(SrampModelUtils.getGenericRelationship(xsdDocument, "NoTargetRel"));
		Assert.assertNull(SrampModelUtils.getGenericRelationship(xsdDocument, "MissingRel"));
	}

	/**
	 * Updates the content of the artifact.
	 * @param entry
	 * @throws Exception
	 */
	private void doUpdateXsdContent(Entry entry) throws Exception {
		XsdDocument xsdDocument = (XsdDocument) SrampAtomUtils.unwrapSrampArtifact(entry);
		String uuid = xsdDocument.getUuid();
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid + "/media"));

		// read the XsdDocument from file
		String artifactFileName = "PO-updated.xsd";
		InputStream xsdStream = null;
		try {
			xsdStream = this.getClass().getResourceAsStream("/sample-files/xsd/" + artifactFileName);
			request.body(MediaType.APPLICATION_XML, xsdStream);
			request.put(Void.class);
		} finally {
			IOUtils.closeQuietly(xsdStream);
		}
	}

	/**
	 * Confirms that the content was updated.
	 * @param content
	 * @throws IOException
	 */
	private void verifyContentUpdated(String content) throws IOException {
		Assert.assertNotNull(content);

		InputStream xsdStream = this.getClass().getResourceAsStream("/sample-files/xsd/PO-updated.xsd");
		try {
			String expectedContent = TestUtils.convertStreamToString(xsdStream);
			Assert.assertEquals(expectedContent, content);
		} finally {
			xsdStream.close();
		}
	}

	/**
	 * Delete the XSD entry with the given uuid.
	 * @param entryId
	 * @throws Exception
	 */
	private void deleteXsdEntry(URI entryId) throws Exception {
		String uuid = entryId.toString();
		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid));
		request.delete(Void.class);
	}

	/**
	 * Verify that the artifact was really deleted.
	 * @throws Exception
	 */
	private void verifyEntryDeleted(URI entryId) throws Exception {
		String uuid = entryId.toString();

		ClientRequest request = new ClientRequest(generateURL("/s-ramp/xsd/XsdDocument/" + uuid));
		try {
			request.get(String.class);
			Assert.fail("Expected an 'Artifact not found.' error here.");
		} catch (SrampAtomException e) {
			Assert.assertEquals("Artifact not found.", e.getMessage());
		}
	}

}
