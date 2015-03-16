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

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.AbstractScmTagAction;

import org.kohsuke.stapler.export.ExportedBean;

/**
 * A Tag Action allows a user to tag a build. Repo doesn't support a solid tag
 * method, so right now we just display the static manifest information needed
 * to recreate the exact state of the repository when the build was ran.
 */
@ExportedBean(defaultVisibility = 999)
public class TagAction extends AbstractScmTagAction {

	/**
	 * Constructs the tag action object. Just call the superclass.
	 *
	 * @param build
	 *            Build which we are interested in tagging
	 */
	TagAction(final AbstractBuild<?, ?> build) {
		super((Run)build);
	}

	/**
	 * Returns the filename to use as the badge. Called by the default badge
	 * jelly file.
	 */
	public String getIconFileName() {
		// TODO: return null if we don't want to show a link (no permissions?)
		// TODO: if we later support actual tagging, we can use star-gold.gif
		// for already tagged builds
		return "star.gif";
	}

	/**
	 * Returns the display name to use for the tag action. Called by the default
	 * badge jelly file.
	 */
	public String getDisplayName() {
		// TODO: adjust name based on build state (tagged already or not)?
		return "Repo Manifest";
	}

	@Override
	public String getTooltip() {
		// TODO: Do we want a custom tool tip?
		return super.getTooltip();
	}

	@Override
	public boolean isTagged() {
		// TODO Support some form of tagging in the future?
		return false;
	}

	/**
	 * Gets a String representation of the static manifest for this repo
	 * snapshot.
	 */
    public String getManifest() {
        final RevisionState revisionState =
            getBuild().getAction(RevisionState.class);
        final String manifest = revisionState.getManifest();
        return manifest;
	}
    
    public static RevisionState getStateForBuild(@SuppressWarnings("rawtypes") AbstractBuild build) {
    	return build.getAction(RevisionState.class);
    }
}
