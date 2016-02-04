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
package hudson.plugins.repo.browser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.plugins.repo.ChangeLogEntry;
import hudson.plugins.repo.ChangeLogEntry.ModifiedFile;
import hudson.plugins.repo.ProjectState;
import hudson.plugins.repo.RepoScm;
import hudson.plugins.repo.RevisionState;
import hudson.plugins.repo.TagAction;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import net.sf.json.JSONObject;

/**
 * Builds changeset information to be displayed in a repository browser view.
 */
public class RepoRepositoryBrowser extends RepositoryBrowser<ChangeLogEntry> {

	private static final long serialVersionUID = 1L;
	private RepoScm scm;
	private static Logger debug = Logger.getLogger("hudson.plugins.repo.RepoScm");

	/**
	 * Build repository browser.
	 */
	@DataBoundConstructor
	public RepoRepositoryBrowser() {
		super();
		AbstractProject<?, ?> project = (AbstractProject<?, ?>) Stapler.getCurrentRequest()
				.findAncestorObject(Job.class);
		scm = (RepoScm) project.getScm();
	}

	private String getBaseUri(final ChangeLogEntry changeSet) {
		AbstractBuild build = changeSet.getParent().build;
		RevisionState state = TagAction.getStateForBuild(build);
		if (state == null) {
			debug.warning("Failed to get project state for build" + build.number);
			return null;
		}
		ProjectState project = state.getProject(changeSet.getPath());
		if (project == null) {
			debug.warning("Failed to get project state for build " + build
					+ " changeset " + changeSet.getPath());
			return null;
		}
		if (StringUtils.isEmpty(project.getFullGitRepositoryUri())) {
			debug.warning("Empty URL fot build " + build + " changeset " + changeSet.getPath());
			return null;
		}

		return StringUtils.removeEnd(project.getFullGitRepositoryUri(), ".git");
	}

	/**
	 * Returns changeset link according to github "schema".
	 * @param changeSet
	 *            changeset to get link for
	 * @throws MalformedURLException
	 *             in case of error
	 */
	@Override
	public URL getChangeSetLink(final ChangeLogEntry changeSet) throws MalformedURLException {
		String uri = getBaseUri(changeSet);
		if (uri == null) {
			return null;
		}
		// Currently only supporting github
		return new URL(uri + "/commit/" + changeSet.getRevision());
	}

	/**
	 * Creates a link to the file diff. http://[GitHib
	 * URL]/commit/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb#diff-N
	 *
	 * @param path
	 *            affected file path
	 * @return diff link
	 * @throws MalformedURLException
	 *             in case of error
	 */
	public URL getDiffLink(final ModifiedFile path) throws MalformedURLException {
		if (path.getEditType() != EditType.EDIT) {
			return null;
		}
		return getDiffLinkRegardlessOfEditType(path);
	}

	/**
	 * Return a diff link regardless of the edit type by appending the index of
	 * the pathname in the changeset.
	 *
	 * @param path
	 * @return url for differences
	 * @throws MalformedURLException
	 *             is thrown if failed to construct URI.
	 */
	private URL getDiffLinkRegardlessOfEditType(final ModifiedFile path)
			throws MalformedURLException {
		final ChangeLogEntry changeSet = path.getChangeSet();
		if (changeSet == null) {
			return null;
		}

		final ArrayList<String> affectedPaths = new ArrayList<String>(changeSet.getAffectedPaths());
		// Github seems to sort the output alphabetically by the path.
		Collections.sort(affectedPaths);
		final String pathAsString = path.getPath();
		final int i = Collections.binarySearch(affectedPaths, pathAsString);
		assert i >= 0;
		return new URL(getChangeSetLink(changeSet), "#diff-" + String.valueOf(i));
	}

	/**
	 * Creates a link to the file. http://[GitHib
	 * URL]/blob/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb/src/main/java/hudson/
	 * plugins/git/browser/GithubWeb.java Github seems to have no URL for
	 * deleted files, so just return a difflink instead.
	 * @param path
	 *            File to get uri to
	 * @throws MalformedURLException
	 *             is thrown if failed to construct URI
	 */
	public URL getFileLink(final ModifiedFile path) throws MalformedURLException {
		if (path.getEditType().equals(EditType.DELETE)) {
			return getDiffLinkRegardlessOfEditType(path);
		} else {
			ChangeLogEntry logEntry = path.getChangeSet();
			if (logEntry == null) {
				debug.fine("Failed to determine log entry for " + path.getPath());
				return null;
			}
			String uri = getBaseUri(logEntry);
			if (uri == null) {
				debug.warning("Failed to get URI for " + path.getPath());
				return null;
			}
			return new URL(uri + "/blob/" + path.getChangeSet().getRevision()
					+ "/" + path.getPath());
		}
	}

	/**
	 * Represents browser descriptor.
	 */
	@Extension
	public static class RepoBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {
		/**
		 * Returns display name.
		 */
		public String getDisplayName() {
			return "repo";
		}

		/**
		 * Creates new instance.
		 * @param jsonObject
		 *            Descriptor.
		 * @param req
		 *            Request.
		 */
		@Override
		public RepoRepositoryBrowser newInstance(final StaplerRequest req,
				final JSONObject jsonObject) {
			return req.bindJSON(RepoRepositoryBrowser.class, jsonObject);
		}
	}

}
