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
package org.overlord.sramp.atom.archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType;

/**
 * Models the archive format defined in the S-RAMP Atom Binding document.
 *
 * @author eric.wittmann@redhat.com
 */
public class SrampArchive {

	private File workDir;

	/**
	 * Creates a new, empty S-RAMP archive.
	 * @throws SrampArchiveException
	 */
	public SrampArchive() throws SrampArchiveException {
		workDir = null;
		try {
			workDir = createWorkDir();
		} catch (IOException e) {
			if (workDir != null && workDir.exists()) {
				try { FileUtils.deleteDirectory(workDir); } catch (IOException e1) { }
			}
			throw new SrampArchiveException("Failed to create archive work directory", e);
		}
	}

	/**
	 * Creates an S-RAMP archive from an existing archive file.
	 * @param file
	 * @throws SrampArchiveException
	 */
	public SrampArchive(File file) throws SrampArchiveException {
		this();
		try {
			ArchiveUtils.unpackToWorkDir(file, this.workDir);
		} catch (IOException e) {
			throw new SrampArchiveException("Failed to unpack S-RAMP archive into work directory", e);
		}
	}

	/**
	 * Create the working directory for this archive.
	 * @throws IOException
	 */
	private static File createWorkDir() throws IOException {
		File tempFile = File.createTempFile("s-ramp-archive", ".work");
		tempFile.delete();
		tempFile.mkdir();
		return tempFile;
	}

	/**
	 * The S-RAMP archive should always be closed when the client is done with it.  This will
	 * clean up all temporary resources created by the archive.
	 * @throws IOException
	 */
	public void close() throws IOException {
		FileUtils.deleteDirectory(workDir);
	}

	/**
	 * Gets all of the entries found in this S-RAMP archive.  It does this by scanning the
	 * archive looking for all *.atom files.  One entry will be returned for each *.atom
	 * file found in the archive (assuming it has associated content and the *.atom file is
	 * properly formatted).
	 * @throws SrampArchiveException
	 */
	public Collection<SrampArchiveEntry> getEntries() throws SrampArchiveException {
		Collection<File> files = FileUtils.listFiles(workDir, new String[] { "atom" }, true);
		Collection<SrampArchiveEntry> entries = new ArrayList<SrampArchiveEntry>(files.size());
		for (File metaDataFile : files) {
			String metaDataAbsPath = metaDataFile.getAbsolutePath();
			File contentFile = new File(metaDataAbsPath.substring(0, metaDataAbsPath.length() - 5));
			if (contentFile.isFile()) {
				String path = contentFile.getAbsolutePath();
				path = path.substring(this.workDir.getAbsolutePath().length() + 1);
				path = path.replace('\\', '/'); // just in case we're in Windows
				entries.add(new SrampArchiveEntry(path, metaDataFile));
			}
		}
		return entries;
	}

	/**
	 * Gets the content {@link InputStream} for the given S-RAMP archive entry.
	 * @param entry the s-ramp archive entry
	 * @return an {@link InputStream} over the artifact content
	 * @throws IOException
	 */
	public InputStream getInputStream(SrampArchiveEntry entry) throws IOException {
		File artifactPath = new File(this.workDir, entry.getPath());
		return FileUtils.openInputStream(artifactPath);
	}

	/**
	 * Adds an entry to the S-RAMP archive.  This method will close the content
	 * {@link InputStream}.
	 * @param path the path in the archive (usually just the name of the artifact)
	 * @param artifact the artifact meta-data
	 * @param content the entry content
	 * @throws SrampArchiveException
	 */
	public void addEntry(String path, BaseArtifactType artifact, InputStream content) throws SrampArchiveException {
		SrampArchiveEntry entry = new SrampArchiveEntry(path, artifact);
		addEntry(entry, content);
	}

	/**
	 * Adds an entry to the S-RAMP archive.  This method will close the content
	 * {@link InputStream}.
	 * @param entry the archive entry
	 * @param content the entry content
	 * @throws SrampArchiveException
	 */
	public void addEntry(SrampArchiveEntry entry, InputStream content) throws SrampArchiveException {
		if (entry.getPath() == null)
			throw new SrampArchiveException("Invalid entry path.");
		if (entry.getMetaData() == null)
			throw new SrampArchiveException("Missing artifact meta-data.");
		File workPath = new File(this.workDir, entry.getPath());
		if (workPath.exists())
			throw new SrampArchiveException("Archive entry already exists.");
		// Create any required parent directories
		workPath.getParentFile().mkdirs();
		File atomWorkPath = new File(this.workDir, entry.getPath() + ".atom");
		writeContent(workPath, content);
		try {
			SrampArchiveJaxbUtils.writeMetaData(atomWorkPath, entry.getMetaData());
		} catch (JAXBException e) {
			throw new SrampArchiveException(e);
		}
	}

	/**
	 * Writes the artifact content to the given working path.
	 * @param workPath
	 * @param content
	 * @throws SrampArchiveException
	 */
	private void writeContent(File workPath, InputStream content) throws SrampArchiveException {
		OutputStream outStream = null;
		try {
			outStream = new FileOutputStream(workPath);
			IOUtils.copy(content, outStream);
		} catch (Throwable t) {
			throw new SrampArchiveException("Error writing content to archive work directory.", t);
		} finally {
			IOUtils.closeQuietly(content);
			IOUtils.closeQuietly(outStream);
		}
	}

	/**
	 * Packs up the current contents of the S-RAMP archive into a single (.zip) file and
	 * returns a reference to it.
	 * @throws SrampArchiveException
	 */
	public File pack() throws SrampArchiveException {
		try {
			File archiveFile = File.createTempFile("s-ramp-archive", ".zip");
			FileOutputStream outputStream = FileUtils.openOutputStream(archiveFile);
			ZipOutputStream zipOutputStream = null;
			try {
				zipOutputStream = new ZipOutputStream(outputStream);
				Collection<SrampArchiveEntry> entries = getEntries();
				for (SrampArchiveEntry entry : entries) {
					packEntry(entry, zipOutputStream);
				}
			} finally {
				IOUtils.closeQuietly(zipOutputStream);
			}
			return archiveFile;
		} catch (Throwable t) {
			throw new SrampArchiveException("Error packing up the S-RAMP archive.", t);
		}
	}

	/**
	 * Pack the given S-RAMP archive entry into the ZIP.
	 * @param entry an s-ramp archive entry
	 * @param zipOutputStream the zip file
	 * @throws IOException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws URISyntaxException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws JAXBException
	 */
	private void packEntry(SrampArchiveEntry entry, ZipOutputStream zipOutputStream) throws IOException, IllegalArgumentException, SecurityException, URISyntaxException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, JAXBException {
		// Store the artifact content in the ZIP
		zipOutputStream.putNextEntry(new ZipEntry(entry.getPath()));
		InputStream contentStream = getInputStream(entry);
		try {
			IOUtils.copy(contentStream, zipOutputStream);
		} finally {
			IOUtils.closeQuietly(contentStream);
		}
		zipOutputStream.closeEntry();

		// Store the meta-data in the ZIP
		zipOutputStream.putNextEntry(new ZipEntry(entry.getPath() + ".atom"));
		try {
			SrampArchiveJaxbUtils.writeMetaData(zipOutputStream, entry.getMetaData());
		} finally {
		}
		zipOutputStream.closeEntry();
	}

}
