/*
 * Copyright 2013 JBoss Inc
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
package org.overlord.sramp.integration.switchyard.expand;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.overlord.sramp.atom.archive.expand.ZipToSrampArchive;
import org.overlord.sramp.atom.archive.expand.ZipToSrampArchiveException;
import org.overlord.sramp.atom.archive.expand.registry.TypeHintInfo;
import org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveProvider;
import org.overlord.sramp.common.ArtifactType;
import org.overlord.sramp.integration.switchyard.model.SwitchYardModel;

/**
 * Provides a SwitchYard version of the {@link ZipToSrampArchive}.
 *
 * @author eric.wittmann@redhat.com
 */
@Component(name = "SwitchYard App to Sramp Archive Provider", immediate = true)
@Service(value = org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveProvider.class)
public class SwitchYardAppToSrampArchiveProvider implements ZipToSrampArchiveProvider {

	private static final Set<String> acceptedTypes = new HashSet<String>();
	private static final Map<String, String> hintsMap;
    static {
        acceptedTypes.add("SwitchYardApplication"); //$NON-NLS-1$
        Map<String, String>aMap = new LinkedHashMap<String,String>();
        aMap.put("WEB-INF/switchyard.xml", SwitchYardModel.SwitchYardApplication); //$NON-NLS-1$
        aMap.put("WEB-INF/classes/META-INF/switchyard.xml", SwitchYardModel.SwitchYardApplication); //$NON-NLS-1$
        aMap.put("META-INF/switchyard.xml", SwitchYardModel.SwitchYardApplication); //$NON-NLS-1$
        hintsMap = Collections.unmodifiableMap(aMap);
    }

    /**
     * Constructor.
     */
    public SwitchYardAppToSrampArchiveProvider() {
    }

    /**
     * @see org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveProvider#accept(org.overlord.sramp.common.ArtifactType)
     */
    @Override
    public boolean accept(ArtifactType artifactType) {
        if (artifactType.isExtendedType()) {
            String extendedType = artifactType.getExtendedType();
            return acceptedTypes.contains(extendedType);
        }
        return false;
    }

    /**
     * @see org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveProvider#createExtractor(org.overlord.sramp.common.ArtifactType, java.io.File)
     */
    @Override
    public ZipToSrampArchive createExtractor(ArtifactType artifactType, File zipFile) throws ZipToSrampArchiveException {
        return new SwitchYardAppToSrampArchive(zipFile);
    }

    /**
     * @see org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveProvider#createExtractor(org.overlord.sramp.common.ArtifactType, java.io.InputStream)
     */
    @Override
    public ZipToSrampArchive createExtractor(ArtifactType artifactType, InputStream zipStream) throws ZipToSrampArchiveException {
        return new SwitchYardAppToSrampArchive(zipStream);
    }

    @Override
   	public TypeHintInfo getArchiveTypeHints() {
   		return new TypeHintInfo(20,hintsMap);
   	}

}
