package hudson.plugins.repo.browser;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.repo.ChangeLogEntry;
import hudson.plugins.repo.ChangeLogEntry.ModifiedFile;
import hudson.plugins.repo.ProjectState;
import hudson.plugins.repo.RepoChangeLogSet;
import hudson.plugins.repo.RepoScm;
import hudson.plugins.repo.RevisionState;
import hudson.plugins.repo.TagAction;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;


public class RepoRepositoryBrowser extends RepositoryBrowser<ChangeLogEntry> {

    private static final long serialVersionUID = 1L;
    private RepoScm scm;
    private static Logger debug = Logger
			.getLogger("hudson.plugins.repo.RepoScm");
    
    @DataBoundConstructor
  	public RepoRepositoryBrowser() {    	
	  super();
	  AbstractProject<?,?> project = (AbstractProject<?,?>)Stapler.getCurrentRequest().findAncestorObject(Job.class);
	  scm = (RepoScm)project.getScm();
    }
    
    private String getBaseUri(ChangeLogEntry changeSet) {
		AbstractBuild build = changeSet.getParent().build;
		//return new URL(url, url.getPath()+"commit/" + changeSet.getId().toString());
		RevisionState state = TagAction.getStateForBuild(build);
		if (state == null){
			debug.warning("Failed to get project state for build" + build.number);
			return null;
		}
		ProjectState project = state.getProject(changeSet.getPath());
		if (project == null){
			debug.warning("Failed to get project state for build " + build + " changeset " + changeSet.getPath());
			return null;
		}
		if (StringUtils.isEmpty(project.getFullGitRepositoryUri())){
			debug.warning("Empty URL fot build " + build + " changeset " + changeSet.getPath());
			return null;
		}
		 			
		return StringUtils.removeEnd(project.getFullGitRepositoryUri(),".git");
    }
    
	@Override
	public URL getChangeSetLink(ChangeLogEntry changeSet) throws IOException {
		String uri = getBaseUri(changeSet);
		if (uri == null){
			return null;
		}
		// Currently only supporting github
		return new URL(uri + "/commit/" + changeSet.getRevision());
	}
	
	 /**
     * Creates a link to the file diff.
     * http://[GitHib URL]/commit/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb#diff-N
     *
     * @param path affected file path
     * @return diff link
     * @throws IOException
     */
    public URL getDiffLink(ModifiedFile path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            return null;
        }
        return getDiffLinkRegardlessOfEditType(path);
    }

    
    /**
     * Return a diff link regardless of the edit type by appending the index of the pathname in the changeset.
     *
     * @param path
     * @return url for differences
     * @throws IOException
     */
    private URL getDiffLinkRegardlessOfEditType(ModifiedFile path) throws IOException {
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
     * Creates a link to the file.
     * http://[GitHib URL]/blob/573670a3bb1f3b939e87f1dee3e99b6bfe281fcb/src/main/java/hudson/plugins/git/browser/GithubWeb.java
     *  Github seems to have no URL for deleted files, so just return
     * a difflink instead.
     *
     * @param path file
     * @return file link
     * @throws IOException
     */
    public URL getFileLink(ModifiedFile path) throws IOException {
        if (path.getEditType().equals(EditType.DELETE)) {
            return getDiffLinkRegardlessOfEditType(path);
        } else {
        	ChangeLogEntry logEntry = path.getChangeSet();
        	if (logEntry == null){
        		debug.fine("Failed to determine log entry for " + path.getPath());
        		return null;
        	}
        	String uri = getBaseUri(logEntry);
    		if (uri == null){
    			debug.warning("Failed to get URI for " + path.getPath());
    			return null;
    		}
    		return new URL(uri + "/blob/" + path.getChangeSet().getRevision()+ "/" + path.getPath());
        }
    }

    @Extension
    public static class RepoBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "repo";
        }

        @Override
		public RepoRepositoryBrowser newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {
			return req.bindJSON(RepoRepositoryBrowser.class, jsonObject);
		}
	}



}
