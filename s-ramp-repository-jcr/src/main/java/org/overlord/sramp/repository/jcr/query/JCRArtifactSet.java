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
package org.overlord.sramp.repository.jcr.query;

import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.BaseArtifactType;
import org.overlord.sramp.repository.jcr.JCRNodeToArtifactFactory;
import org.overlord.sramp.repository.jcr.JCRRepositoryFactory;
import org.overlord.sramp.repository.query.ArtifactSet;

/**
 * A JCR implementation of an {@link ArtifactSet}.
 *
 * @author eric.wittmann@redhat.com
 */
public class JCRArtifactSet implements ArtifactSet, Iterator<BaseArtifactType> {

	private Session session;
	private NodeIterator jcrNodes;
	private boolean logoutOnClose = true;

	/**
	 * Constructor.
	 * @param session
	 * @param jcrNodes
	 */
	public JCRArtifactSet(Session session, NodeIterator jcrNodes) {
		this.session = session;
		this.jcrNodes = jcrNodes;
	}

    /**
     * Constructor.
     * @param session
     * @param jcrNodes
     * @param logoutOnClose
     */
    public JCRArtifactSet(Session session, NodeIterator jcrNodes, boolean logoutOnClose) {
        this(session, jcrNodes);
        this.logoutOnClose = logoutOnClose;
    }

	/**
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<BaseArtifactType> iterator() {
		return this;
	}

	/**
	 * @see org.overlord.sramp.common.repository.query.ArtifactSet#size()
	 */
	@Override
	public long size() {
		return this.jcrNodes.getSize();
	}

	/**
	 * @see org.overlord.sramp.common.repository.query.ArtifactSet#close()
	 */
	@Override
	public void close() {
	    if (logoutOnClose)
	        JCRRepositoryFactory.logoutQuietly(this.session);
	}

	/**
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		return this.jcrNodes.hasNext();
	}

	/**
	 * @see java.util.Iterator#next()
	 */
	@Override
	public BaseArtifactType next() {
		Node jcrNode = this.jcrNodes.nextNode();
		return JCRNodeToArtifactFactory.createArtifact(this.session, jcrNode);
	}

	/**
	 * @see java.util.Iterator#remove()
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}
