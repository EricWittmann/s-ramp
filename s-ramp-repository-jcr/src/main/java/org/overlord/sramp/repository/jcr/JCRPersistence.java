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
package org.overlord.sramp.repository.jcr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.UUID;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.QueryResult;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.modeshape.jcr.JcrRepository.QueryLanguage;
import org.modeshape.jcr.api.JcrTools;
import org.overlord.sramp.ArtifactType;
import org.overlord.sramp.repository.DerivedArtifacts;
import org.overlord.sramp.repository.DerivedArtifactsCreationException;
import org.overlord.sramp.repository.PersistenceManager;
import org.overlord.sramp.repository.RepositoryException;
import org.overlord.sramp.repository.derived.ArtifactDeriver;
import org.overlord.sramp.repository.derived.ArtifactDeriverFactory;
import org.overlord.sramp.repository.jcr.ArtifactToJCRNodeVisitor.JCRReferenceFactory;
import org.overlord.sramp.repository.jcr.util.DeleteOnCloseFileInputStream;
import org.overlord.sramp.repository.jcr.util.JCRUtils;
import org.overlord.sramp.visitors.ArtifactVisitorHelper;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.DerivedArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.UserDefinedArtifactType;
import org.s_ramp.xmlns._2010.s_ramp.XmlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JCR implementation of both the {@link PersistenceManager} and the {@link DerivedArtifacts}
 * interfaces.  By implementing both of these interfaces, this class provides a JCR backend implementation
 * of the S-RAMP repository.
 *
 * This particular implementation leverages the ModeShape sequencing feature to assist with the
 * creation of the S-RAMP derived artifacts.
 */
public class JCRPersistence implements PersistenceManager, DerivedArtifacts {

    private static Logger log = LoggerFactory.getLogger(JCRPersistence.class);

    /**
     * Default constructor.
     * @throws RepositoryException
     * @throws IOException
     */
    public JCRPersistence() throws RepositoryException, IOException {
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#persistArtifact(java.lang.String, org.overlord.sramp.ArtifactType, java.io.InputStream)
     */
    @Override
	public BaseArtifactType persistArtifact(String name, ArtifactType artifactType, InputStream content) throws RepositoryException {
        Session session = null;
        try {
            session = JCRRepository.getSession();
            JcrTools tools = new JcrTools();
            String uuid = UUID.randomUUID().toString();
            String artifactPath = MapToJCRPath.getArtifactPath(uuid, artifactType);
            log.debug("Uploading file {} to JCR.",name);

            Node artifactNode = tools.uploadFile(session, artifactPath, content);
            JCRUtils.setArtifactContentMimeType(artifactNode, artifactType.getMimeType());

            String jcrMixinName = artifactType.getArtifactType().getApiType().value();
            jcrMixinName = JCRConstants.SRAMP_ + StringUtils.uncapitalize(jcrMixinName);
            artifactNode.addMixin(jcrMixinName);
            //BaseArtifactType
            artifactNode.setProperty(JCRConstants.SRAMP_UUID, uuid);
            artifactNode.setProperty(JCRConstants.SRAMP_NAME, name);
            artifactNode.setProperty(JCRConstants.SRAMP_ARTIFACT_MODEL, artifactType.getArtifactType().getModel());
            artifactNode.setProperty(JCRConstants.SRAMP_ARTIFACT_TYPE, artifactType.getArtifactType().getType());
            //UserDefined
            if (UserDefinedArtifactType.class.isAssignableFrom(artifactType.getArtifactType().getTypeClass())) {
                // read the encoding from the header
                artifactNode.setProperty(JCRConstants.SRAMP_USER_TYPE, artifactType.getUserType());
            }
            //XMLDocument
            if (XmlDocument.class.isAssignableFrom(artifactType.getArtifactType().getTypeClass())) {
                // read the encoding from the header
                artifactNode.setProperty(JCRConstants.SRAMP_CONTENT_ENCODING, "UTF-8");
            }

            log.debug("Successfully saved {} to node={}",name, uuid);
            session.save();
            if (log.isDebugEnabled()) {
                printArtifactGraph(uuid, artifactType);
            }
            //now create the S-RAMP Artifact object from the JCR node
            BaseArtifactType baseTypeArtifact = JCRNodeToArtifactFactory.createArtifact(session, artifactNode, artifactType);
            return baseTypeArtifact;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
        	IOUtils.closeQuietly(content);
            JCRRepository.logoutQuietly(session);
        }
    }

    /**
     * @see org.overlord.sramp.repository.DerivedArtifacts#deriveArtifacts(org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType)
     */
    @Override
    public Collection<DerivedArtifactType> deriveArtifacts(BaseArtifactType artifact)
    		throws DerivedArtifactsCreationException {
    	InputStream content = null;
    	try {
    		content = getArtifactContent(artifact.getUuid(), ArtifactType.valueOf(artifact));
	    	ArtifactDeriver deriver = ArtifactDeriverFactory.createArtifactDeriver(ArtifactType.valueOf(artifact));
	    	return deriver.derive(artifact, content);
    	} catch (RepositoryException e) {
			throw new DerivedArtifactsCreationException(e);
		} catch (IOException e) {
			throw new DerivedArtifactsCreationException(e);
		} finally {
    		IOUtils.closeQuietly(content);
    	}
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#persistDerivedArtifacts(org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType, java.util.Collection)
     */
    @Override
    public void persistDerivedArtifacts(BaseArtifactType sourceArtifact,
    		Collection<DerivedArtifactType> artifacts) throws RepositoryException {
        Session session = null;
        try {
            session = JCRRepository.getSession();
            JcrTools tools = new JcrTools();

            // Get the JCR node for the source artifact
            ArtifactType sourceArtifactType = ArtifactType.valueOf(sourceArtifact);
			String sourceArtifactPath = MapToJCRPath.getArtifactPath(sourceArtifact.getUuid(), sourceArtifactType);
            if (!session.nodeExists(sourceArtifactPath)) {
            	throw new RepositoryException("Failed to find JCR node for source artifact with UUID: " + sourceArtifact.getUuid());
            }
            Node sourceArtifactNode = session.getNode(sourceArtifactPath);

            // Persist each of the derived nodes
            JCRReferenceFactoryImpl referenceFactory = new JCRReferenceFactoryImpl(session);
            for (DerivedArtifactType derivedArtifact : artifacts) {
                String uuid = UUID.randomUUID().toString();
                ArtifactType derivedArtifactType = ArtifactType.valueOf(derivedArtifact);
                String jcrNodeName = derivedArtifactType.getArtifactType().getApiType().value();
                jcrNodeName = JCRConstants.SRAMP_ + StringUtils.uncapitalize(jcrNodeName);

                Node derivedArtifactNode = tools.findOrCreateChild(sourceArtifactNode, derivedArtifact.getName(), jcrNodeName);

                derivedArtifactNode.setProperty(JCRConstants.SRAMP_UUID, uuid);
                derivedArtifactNode.setProperty(JCRConstants.SRAMP_ARTIFACT_MODEL, derivedArtifactType.getArtifactType().getModel());
                derivedArtifactNode.setProperty(JCRConstants.SRAMP_ARTIFACT_TYPE, derivedArtifactType.getArtifactType().getType());

				ArtifactToJCRNodeVisitor visitor = new ArtifactToJCRNodeVisitor(derivedArtifactNode, referenceFactory);
                ArtifactVisitorHelper.visitArtifact(visitor, derivedArtifact);
                if (visitor.hasError())
                	throw visitor.getError();

                log.debug("Successfully saved derived artifact {} to node={}", derivedArtifact.getName(), uuid);
            }

            session.save();

            if (log.isDebugEnabled()) {
                printArtifactGraph(sourceArtifact.getUuid(), sourceArtifactType);
            }
        } catch (RepositoryException e) {
        	throw e;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
            JCRRepository.logoutQuietly(session);
        }
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#getArtifact(java.lang.String, org.overlord.sramp.ArtifactType)
     */
    @Override
    public BaseArtifactType getArtifact(String uuid, ArtifactType type) throws RepositoryException {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(uuid, type);

        try {
            session = JCRRepository.getSession();
            if (session.nodeExists(artifactPath)) {
	            Node artifactNode = session.getNode(artifactPath);
	            // Create an artifact from the sequenced node
	            return JCRNodeToArtifactFactory.createArtifact(session, artifactNode, type);
            } else {
            	return null;
            }
        } catch (RepositoryException re) {
        	throw re;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
            JCRRepository.logoutQuietly(session);
        }
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#getArtifactContent(java.lang.String, org.overlord.sramp.ArtifactType)
     */
    @Override
    public InputStream getArtifactContent(String uuid, ArtifactType type) throws RepositoryException {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(uuid, type);

        try {
            session = JCRRepository.getSession();
            Node artifactNode = session.getNode(artifactPath);
            Node artifactContentNode = artifactNode.getNode("jcr:content");
			File tempFile = saveToTempFile(artifactContentNode);
			return new DeleteOnCloseFileInputStream(tempFile);
        } catch (Throwable t) {
        	throw new RepositoryException(t);
		} finally {
            JCRRepository.logoutQuietly(session);
        }
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#updateArtifact(org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType, org.overlord.sramp.ArtifactType)
     */
    @Override
    public void updateArtifact(BaseArtifactType artifact, ArtifactType type) throws RepositoryException {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(artifact.getUuid(), type);

        try {
            session = JCRRepository.getSession();
            Node artifactNode = session.getNode(artifactPath);
            if (artifactNode == null) {
            	throw new RepositoryException("No artifact found with UUID: " + artifact.getUuid());
            }
            ArtifactToJCRNodeVisitor visitor = new ArtifactToJCRNodeVisitor(artifactNode, new JCRReferenceFactoryImpl(session));
            ArtifactVisitorHelper.visitArtifact(visitor, artifact);
            if (visitor.hasError())
            	throw visitor.getError();
            session.save();
        } catch (RepositoryException e) {
        	throw e;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
            JCRRepository.logoutQuietly(session);
        }

        if (log.isDebugEnabled()) {
            printArtifactGraph(artifact.getUuid(), type);
        }
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#updateArtifactContent(java.lang.String, org.overlord.sramp.ArtifactType, java.io.InputStream)
     */
    @Override
    public void updateArtifactContent(String uuid, ArtifactType artifactType, InputStream content) throws RepositoryException {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(uuid, artifactType);

        try {
            session = JCRRepository.getSession();
            Node artifactNode = session.getNode(artifactPath);
            if (artifactNode == null) {
            	throw new RepositoryException("No artifact found with UUID: " + uuid);
            }
            JcrTools tools = new JcrTools();
            tools.uploadFile(session, artifactPath, content);

            session.save();
        } catch (RepositoryException e) {
        	throw e;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
            JCRRepository.logoutQuietly(session);
            IOUtils.closeQuietly(content);
        }
    }

    /**
     * @see org.overlord.sramp.repository.PersistenceManager#deleteArtifact(java.lang.String, org.overlord.sramp.ArtifactType)
     */
    @Override
    public void deleteArtifact(String uuid, ArtifactType artifactType) throws RepositoryException {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(uuid, artifactType);

        try {
            session = JCRRepository.getSession();
            if (session.nodeExists(artifactPath)) {
            	session.getNode(artifactPath).remove();
            } else {
            	throw new RepositoryException("Artifact not found.");
            }
            session.save();
        } catch (RepositoryException e) {
        	throw e;
        } catch (Throwable t) {
        	throw new RepositoryException(t);
        } finally {
        	JCRRepository.logoutQuietly(session);
        }
    }

	/**
     * @see org.overlord.sramp.repository.PersistenceManager#printArtifactGraph(java.lang.String, org.overlord.sramp.ArtifactType)
     */
    @Override
    public void printArtifactGraph(String uuid, ArtifactType type) {
        Session session = null;
        String artifactPath = MapToJCRPath.getArtifactPath(uuid, type);
        try {
            session = JCRRepository.getSession();
            Node artifactNode = session.getNode(artifactPath);
            JcrTools tools = new JcrTools();
            tools.printSubgraph(artifactNode);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JCRRepository.logoutQuietly(session);
        }
    }

	/**
	 * Saves binary content from the given JCR content (jcr:content) node to a temporary
	 * file.
	 * @param jcrContentNode
	 * @param tempFile
	 * @throws Exception
	 */
	private File saveToTempFile(Node jcrContentNode) throws Exception {
		File file = File.createTempFile("sramp", ".jcr");
		Binary binary = null;
		InputStream content = null;
		OutputStream tempFileOS = null;

		try {
			binary = jcrContentNode.getProperty("jcr:data").getBinary();
			content = binary.getStream();
			tempFileOS = new FileOutputStream(file);
			IOUtils.copy(content, tempFileOS);
		} finally {
			IOUtils.closeQuietly(content);
			IOUtils.closeQuietly(tempFileOS);
			if (binary != null)
				binary.dispose();
		}

		return file;
	}
	@Override
	public void shutdown() {
	    JCRRepository.shutdown();
	}

	/**
	 * An impl of a JCR reference factory.
	 */
	private static class JCRReferenceFactoryImpl implements JCRReferenceFactory {

		private Session session;

		/**
		 * Constructor.
		 * @param session
		 */
		public JCRReferenceFactoryImpl(Session session) {
			this.session = session;
		}

    	/**
    	 * @see org.overlord.sramp.repository.jcr.ArtifactToJCRNodeVisitor.JCRReferenceFactory#createReference(java.lang.String)
    	 */
    	@Override
    	public Value createReference(String uuid) {
			try {
	            javax.jcr.query.QueryManager jcrQueryManager = session.getWorkspace().getQueryManager();
	            String jcrSql2Query = String.format("SELECT * FROM [sramp:baseArtifactType] WHERE [sramp:uuid] = '%1$s'", uuid);
	            javax.jcr.query.Query jcrQuery = jcrQueryManager.createQuery(jcrSql2Query, QueryLanguage.JCR_SQL2);
	            QueryResult jcrQueryResult = jcrQuery.execute();
	            NodeIterator jcrNodes = jcrQueryResult.getNodes();
	            if (!jcrNodes.hasNext()) {
	            	throw new Exception("No artifact found with UUID: " + uuid);
	            }
	            if (jcrNodes.getSize() > 1) {
	            	throw new Exception("Too many artifacts found with UUID: " + uuid);
	            }
	            Node node = jcrNodes.nextNode();
	            return session.getValueFactory().createValue(node, false);
			} catch (Exception e) {
				log.error("Error creating JCR reference to S-RAMP artifact with UUID: " + uuid, e);
			}
			return null;
		}
	}

}
