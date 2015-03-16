/*
 * The MIT License
 *
 * Copyright (c) 2010, Brad Larson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.repo;

import hudson.Util;
import hudson.scm.SCMRevisionState;

import java.io.PrintStream;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A RevisionState records the state of the repository for a particular build.
 * It is used to see what changed from build to build.
 */
@SuppressWarnings("serial")
public class RevisionState extends SCMRevisionState implements Serializable {

	private final String manifest;
	private final Map<String, ProjectState> projects =
			new TreeMap<String, ProjectState>();
	private final String branch;

	private static Logger debug =
		Logger.getLogger("hudson.plugins.repo.RevisionState");

	/**
	 * Creates a new RepoRevisionState.
	 *
	 * @param manifest
	 *            A string representation of the static manifest XML file
	 * @param manifestRevision
     *            Git hash of the manifest repo
	 * @param branch
	 *            The branch of the manifest project
	 * @param logger
	 *            A PrintStream for logging errors
	 */
	public RevisionState(final String manifest, final String manifestRevision, String manifestRepositoryUrl,
            final String branch, final PrintStream logger) {
		this.manifest = manifest;
		this.branch = branch;
		try {
			final InputSource xmlSource = new InputSource();
			xmlSource.setCharacterStream(new StringReader(manifest));
			final Document doc =
					DocumentBuilderFactory.newInstance().newDocumentBuilder()
							.parse(xmlSource);

			if (!doc.getDocumentElement().getNodeName().equals("manifest")) {
				logger.println("Error - malformed manifest");
				return;
			}
			Map<String,Element> remotes = parseManifestRemotes(doc.getElementsByTagName("remote"));
			Map<String,String> defaults = parseManifestDefaults(doc.getElementsByTagName("default"));
			final NodeList projectNodes = doc.getElementsByTagName("project");
			final int numProjects = projectNodes.getLength();
			for (int i = 0; i < numProjects; i++) {
				final Element projectElement = (Element) projectNodes.item(i);
				String path =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("path"));
				final String serverPath =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("name"));
				final String revision =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("revision"));
				final String projectUri = getProjectGitURI(projectElement, remotes, defaults);
				if (path == null) {
					// 'repo manifest -o' doesn't output a path if it is the
					// same as the server path, even if the path is specified.
					path = serverPath;
				}
				if (path != null && serverPath != null && revision != null) {
					projects.put(path, ProjectState.constructCachedInstance(
							path, serverPath, revision, projectUri));
					if (logger != null) {
						logger.println("Added a project: " + path
								+ " at revision: " + revision);
					}
				}
			}
			
            final String manifestP = ".repo/manifests.git";
            projects.put(manifestP, ProjectState.constructCachedInstance(
                        manifestP, manifestP, manifestRevision, manifestRepositoryUrl));
            if (logger != null) {
                logger.println("Manifest at revision: " + manifestRevision);
            }


		} catch (final Exception e) {
			logger.println(e);				
			return;
		}
	}
	
	
	private Map<String,Element> parseManifestRemotes(final NodeList remotesSection){ 
		Map<String,Element> remotes = new HashMap<String, Element>();
		if (remotesSection == null){
			return remotes;
		}
		
		final int numRemotes = remotesSection.getLength();
		for (int i = 0; i < numRemotes; i++) {
			final Element projectElement = (Element) remotesSection.item(i);
			final String remoteName = Util.fixEmptyAndTrim(projectElement.getAttribute("name"));
			remotes.put(remoteName, projectElement);
		}
		return remotes;
	}
	
	private Map<String,String> parseManifestDefaults(final NodeList remotesSection){
		Map<String,String> defaults = new HashMap<String, String>();
		if ((remotesSection == null) || (remotesSection.getLength() == 0)){
			return defaults;
		}
		final Element defaultElement = (Element) remotesSection.item(0);
		NamedNodeMap attributes = defaultElement.getAttributes();
		for (int i =0; i < attributes.getLength(); ++i) {
			Node attr = attributes.item(i);
			defaults.put(attr.getNodeName(),attr.getNodeValue());
		}
		return defaults;
	}
	
	private String getProjectGitURI(final Element projectElement, 
									Map<String,Element> remotes, 
									Map<String,String> manifestDefaults) {
		final String serverPath = Util.
				fixEmptyAndTrim(projectElement.getAttribute("name"));
		final String projectRemote = 
				Util.fixEmptyAndTrim(projectElement.getAttribute("remote"));
		String remoteBase;
		if (StringUtils.isEmpty(projectRemote)) {
			//${remote_fetch}/${project_name}.git
			if (!manifestDefaults.containsKey("remote")){
			   debug.warning("Failed to configure URI");
			   return "";
			}
			remoteBase = manifestDefaults.get("remote");
		} else { 
			if (!remotes.containsKey(projectRemote)) {		
				debug.warning("Failed to configure URI cannot find remote");
				return "";
			}
			remoteBase = remotes.get(projectRemote).getAttribute("fetch");
		} 		
		return remoteBase + "/" + serverPath + ".git";
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof RevisionState) {
			final RevisionState other = (RevisionState) obj;
			if (branch == null) {
				if (other.branch != null) {
					return false;
				}
				return projects.equals(other.projects);
			}
			return branch.equals(other.branch)
					&& projects.equals(other.projects);
		}
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return (branch != null ? branch.hashCode() : 0)
			^ (manifest != null ? manifest.hashCode() : 0)
			^ projects.hashCode();
	}

	/**
	 * Returns the manifest repository's branch name when this state was
	 * created.
	 */
	public String getBranch() {
		return branch;
	}

	/**
	 * Returns the static XML manifest for this repository state in String form.
	 */
	public String getManifest() {
		return manifest;
	}
	
	public ProjectState getProject(String projectName){
		if (!projects.containsKey(projectName)){
			return null;
		}
		return projects.get(projectName);
	}
	

	/**
	 * Returns the revision for the repository at the specified path.
	 *
	 * @param path
	 *            The path to the repository in which we are interested.
	 * @return the SHA1 revision of the repository.
	 */
	public String getRevision(final String path) {
		ProjectState project = projects.get(path);
		return project == null ? null : project.getRevision();
	}

	/**
	 * Calculate what has changed from a specified previous repository state.
	 *
	 * @param previousState
	 *            The previous repository state in which we are interested
	 * @return A List of ProjectStates from the previous repo state which have
	 *         since been updated.
	 */
	public List<ProjectState> whatChanged(final RevisionState previousState) {
		final List<ProjectState> changes = new ArrayList<ProjectState>();
		if (previousState == null) {
			// Everything is new. The change log would include every change,
			// which might be a little unwieldy (and take forever to
			// generate/parse). Instead, we will return null (no changes)
			debug.log(Level.FINE, "Everything is new");
			return null;
		}
		final Set<String> keys = projects.keySet();
		HashMap<String, ProjectState> previousStateCopy =
				new HashMap<String, ProjectState>(previousState.projects);
		for (final String key : keys) {
			final ProjectState status = previousStateCopy.get(key);
			if (status == null) {
				// This is a new project, just added to the manifest.
				final ProjectState newProject = projects.get(key);
				debug.log(Level.FINE, "New project: " + key);
				changes.add(ProjectState.constructCachedInstance(
						newProject.getPath(), newProject.getServerPath(),
						null, newProject.getFullGitRepositoryUri()));
			} else if (!status.equals(projects.get(key))) {
				changes.add(previousStateCopy.get(key));
			}
			previousStateCopy.remove(key);
		}
		changes.addAll(previousStateCopy.values());
		return changes;
	}
}
