/*
 * Copyright 2012 JBoss Inc
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
package org.overlord.sramp.repository.derived;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.overlord.sramp.query.xpath.StaticNamespaceContext;
import org.s_ramp.xmlns._2010.s_ramp.AttributeDeclaration;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.ComplexTypeDeclaration;
import org.s_ramp.xmlns._2010.s_ramp.DerivedArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.DocumentArtifactEnum;
import org.s_ramp.xmlns._2010.s_ramp.DocumentArtifactTarget;
import org.s_ramp.xmlns._2010.s_ramp.ElementDeclaration;
import org.s_ramp.xmlns._2010.s_ramp.SimpleTypeDeclaration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Creates derived content from an XSD document.  This will create the derived content as
 * defined in the XML Schema model found in the s-ramp specification.  The following derived
 * artifact types will (potentially) be created:
 *
 * <ul>
 *   <li>AttributeDeclaration</li>
 *   <li>ElementDeclaration</li>
 *   <li>ComplexTypeDeclaration</li>
 *   <li>SimpleTypeDeclaration</li>
 * </ul>
 *
 * @author eric.wittmann@redhat.com
 */
public class XsdDeriver implements ArtifactDeriver {

	/**
	 * Constructor.
	 */
	public XsdDeriver() {
	}

	/**
	 * @see org.overlord.sramp.repository.derived.ArtifactDeriver#derive(org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType, java.io.InputStream)
	 */
	@Override
	public Collection<DerivedArtifactType> derive(BaseArtifactType artifact, InputStream content) throws IOException {
		Collection<DerivedArtifactType> derivedArtifacts = new LinkedList<DerivedArtifactType>();

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setValidating(false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(content);
			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();
			StaticNamespaceContext nsCtx = new StaticNamespaceContext();
			nsCtx.addMapping("xs", "http://www.w3.org/2001/XMLSchema");
			nsCtx.addMapping("xsd", "http://www.w3.org/2001/XMLSchema");
			xpath.setNamespaceContext(nsCtx);

			processElementDeclarations(derivedArtifacts, artifact, document, xpath);
			processAttributeDeclarations(derivedArtifacts, artifact, document, xpath);
			processSimpleTypeDeclarations(derivedArtifacts, artifact, document, xpath);
			processComplexTypeDeclarations(derivedArtifacts, artifact, document, xpath);
		} catch (Exception e) {
			throw new IOException(e);
		}

		return derivedArtifacts;
	}

	/**
	 * Processes the global element declarations found in the schema.
	 * @param derivedArtifacts
	 * @param sourceArtifact
	 * @param document
	 * @param xpath
	 * @throws XPathExpressionException
	 */
	private void processElementDeclarations(Collection<DerivedArtifactType> derivedArtifacts,
			BaseArtifactType sourceArtifact, Document document, XPath xpath) throws XPathExpressionException {
		String targetNS = document.getDocumentElement().getAttribute("targetNamespace");

		// xpath expression to find all global element decls
		XPathExpression expr = xpath.compile("/xsd:schema/xsd:element");
		NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
		for (int idx = 0; idx < nodes.getLength(); idx++) {
			Element node = (Element) nodes.item(idx);
			if (node.hasAttribute("name")) {
				String nsName = node.getAttribute("name");
				ElementDeclaration elementDecl = new ElementDeclaration();
				elementDecl.setName(nsName);
				elementDecl.setNamespace(targetNS);
				elementDecl.setNCName(nsName);
				DocumentArtifactTarget related = new DocumentArtifactTarget();
				related.setValue(sourceArtifact.getUuid());
				related.setArtifactType(DocumentArtifactEnum.fromValue(sourceArtifact.getArtifactType()));
				elementDecl.setRelatedDocument(related);
				derivedArtifacts.add(elementDecl);
			}
		}
	}

	/**
	 * Processes the global attribute declarations found in the schema.
	 * @param derivedArtifacts
	 * @param sourceArtifact
	 * @param document
	 * @param xpath
	 * @throws XPathExpressionException
	 */
	private void processAttributeDeclarations(Collection<DerivedArtifactType> derivedArtifacts,
			BaseArtifactType sourceArtifact, Document document, XPath xpath) throws XPathExpressionException {
		String targetNS = document.getDocumentElement().getAttribute("targetNamespace");

		// xpath expression to find all global attribute decls
		XPathExpression expr = xpath.compile("/xsd:schema/xsd:attribute");
		NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
		for (int idx = 0; idx < nodes.getLength(); idx++) {
			Element node = (Element) nodes.item(idx);
			if (node.hasAttribute("name")) {
				String nsName = node.getAttribute("name");
				AttributeDeclaration attributeDecl = new AttributeDeclaration();
				attributeDecl.setName(nsName);
				attributeDecl.setNamespace(targetNS);
				attributeDecl.setNCName(nsName);
				DocumentArtifactTarget related = new DocumentArtifactTarget();
				related.setValue(sourceArtifact.getUuid());
				related.setArtifactType(DocumentArtifactEnum.fromValue(sourceArtifact.getArtifactType()));
				attributeDecl.setRelatedDocument(related);
				derivedArtifacts.add(attributeDecl);
			}
		}
	}

	/**
	 * Processes the global simple type declarations found in the schema.
	 * @param derivedArtifacts
	 * @param sourceArtifact
	 * @param document
	 * @param xpath
	 * @throws XPathExpressionException
	 */
	private void processSimpleTypeDeclarations(Collection<DerivedArtifactType> derivedArtifacts,
			BaseArtifactType sourceArtifact, Document document, XPath xpath) throws XPathExpressionException {
		String targetNS = document.getDocumentElement().getAttribute("targetNamespace");

		// xpath expression to find all global simple type decls
		XPathExpression expr = xpath.compile("/xsd:schema/xsd:simpleType");
		NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
		for (int idx = 0; idx < nodes.getLength(); idx++) {
			Element node = (Element) nodes.item(idx);
			if (node.hasAttribute("name")) {
				String nsName = node.getAttribute("name");
				SimpleTypeDeclaration simpleTypeDecl = new SimpleTypeDeclaration();
				simpleTypeDecl.setName(nsName);
				simpleTypeDecl.setNamespace(targetNS);
				simpleTypeDecl.setNCName(nsName);
				DocumentArtifactTarget related = new DocumentArtifactTarget();
				related.setValue(sourceArtifact.getUuid());
				related.setArtifactType(DocumentArtifactEnum.fromValue(sourceArtifact.getArtifactType()));
				simpleTypeDecl.setRelatedDocument(related);
				derivedArtifacts.add(simpleTypeDecl);
			}
		}
	}

	/**
	 * Processes the global complex type declarations found in the schema.
	 * @param derivedArtifacts
	 * @param sourceArtifact
	 * @param document
	 * @param xpath
	 * @throws XPathExpressionException
	 */
	private void processComplexTypeDeclarations(Collection<DerivedArtifactType> derivedArtifacts,
			BaseArtifactType sourceArtifact, Document document, XPath xpath) throws XPathExpressionException {
		String targetNS = document.getDocumentElement().getAttribute("targetNamespace");

		// xpath expression to find all global complex type decls
		XPathExpression expr = xpath.compile("/xsd:schema/xsd:complexType");
		NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
		for (int idx = 0; idx < nodes.getLength(); idx++) {
			Element node = (Element) nodes.item(idx);
			if (node.hasAttribute("name")) {
				String nsName = node.getAttribute("name");
				ComplexTypeDeclaration complexTypeDecl = new ComplexTypeDeclaration();
				complexTypeDecl.setName(nsName);
				complexTypeDecl.setNamespace(targetNS);
				complexTypeDecl.setNCName(nsName);
				DocumentArtifactTarget related = new DocumentArtifactTarget();
				related.setValue(sourceArtifact.getUuid());
				related.setArtifactType(DocumentArtifactEnum.fromValue(sourceArtifact.getArtifactType()));
				complexTypeDecl.setRelatedDocument(related);
				derivedArtifacts.add(complexTypeDecl);
			}
		}
	}

}
