/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.repo;


import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.xml.parsers.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.util.KnownRepositories;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.IWorkingSet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class is repsible for maintaining the UI's list of known repositories,
 * and a list of known tags within each of those repositories.
 * 
 * It also provides a number of useful methods for assisting in repository operations.
 */
public class RepositoryManager {
	// old state file
	private static final String STATE_FILE = ".repositoryManagerState"; //$NON-NLS-1$
	private static final int STATE_FILE_VERSION_1 = -1;
	// new state file
	private static final String REPOSITORIES_VIEW_FILE = "repositoriesView.xml"; //$NON-NLS-1$
	private static final String COMMENT_HIST_FILE = "commitCommentHistory.xml"; //$NON-NLS-1$
	static final String ELEMENT_COMMIT_COMMENT = "CommitComment"; //$NON-NLS-1$
	static final String ELEMENT_COMMIT_HISTORY = "CommitComments"; //$NON-NLS-1$

	private Map repositoryRoots = new HashMap();
	
	List listeners = new ArrayList();

	// The previously remembered comment
	static String[] previousComments = new String[0];
	
	public static boolean notifyRepoView = true;
	
	// Cache of changed repository roots
	private int notificationLevel = 0;
	private Map changedRepositories = new HashMap();
	
	static final int MAX_COMMENTS = 10;
	
	/**
	 * Answer an array of all known remote roots.
	 */
	public ICVSRepositoryLocation[] getKnownRepositoryLocations() {
		return KnownRepositories.getInstance().getRepositories();
	}
	
	/**
	 * Method getRepositoryRoots.
	 * @param iCVSRepositoryLocations
	 * @return RepositoryRoot[]
	 */
	private RepositoryRoot[] getRepositoryRoots(ICVSRepositoryLocation[] locations) {
		List roots = new ArrayList();
		for (int i = 0; i < locations.length; i++) {
			ICVSRepositoryLocation location = locations[i];
			RepositoryRoot root = getRepositoryRootFor(location);
			if (root != null)
				roots.add(root);
		}
		return (RepositoryRoot[]) roots.toArray(new RepositoryRoot[roots.size()]);
	}
	
	public RepositoryRoot[] getKnownRepositoryRoots() {
		return getRepositoryRoots(getKnownRepositoryLocations());
	}
	
	/**
	 * Get the list of known branch tags for a given remote root.
	 */
	public CVSTag[] getKnownTags(ICVSFolder project, int tagType) {
		try {
			CVSTag[] tags = getKnownTags(project);
			Set result = new HashSet();
			for (int i = 0; i < tags.length; i++) {
				CVSTag tag = tags[i];
				if (tag.getType() == tagType)
					result.add(tag);
			}

			return (CVSTag[])result.toArray(new CVSTag[result.size()]);
		} catch(CVSException e) {
			CVSUIPlugin.log(e);
			return new CVSTag[0];
		}
	}
	
	/**
	 * Get the list of known version tags for a given project.
	 */
	public CVSTag[] getKnownTags(ICVSRepositoryLocation location, int tagType) {
		Set result = new HashSet();
		RepositoryRoot root = (RepositoryRoot)repositoryRoots.get(location.getLocation());
		if (root != null) {
			String[] paths = root.getKnownRemotePaths();
			for (int i = 0; i < paths.length; i++) {
				String path = paths[i];
				CVSTag[] tags = root.getAllKnownTags(path);
				for (int j = 0; j < tags.length; j++) {
					CVSTag tag = tags[j];
					if (tag.getType() == tagType)
						result.add(tag);
				}
			}
		}
		return (CVSTag[])result.toArray(new CVSTag[0]);
	}
	
	/**
	 * Method getKnownTags.
	 * @param repository
	 * @param set
	 * @param i
	 * @param monitor
	 * @return CVSTag[]
	 */
	public CVSTag[] getKnownTags(ICVSRepositoryLocation repository, IWorkingSet set, int tagType, IProgressMonitor monitor) throws CVSException {
		if (set == null) {
			return getKnownTags(repository, tagType);
		}
		ICVSRemoteResource[] folders = getFoldersForTag(repository, CVSTag.DEFAULT, monitor);
		folders = filterResources(set, folders);
		Set tags = new HashSet();
		for (int i = 0; i < folders.length; i++) {
			ICVSRemoteFolder folder = (ICVSRemoteFolder)folders[i];
			tags.addAll(Arrays.asList(getKnownTags(folder, tagType)));
		}
		return (CVSTag[]) tags.toArray(new CVSTag[tags.size()]);
	}
	
	public CVSTag[] getKnownTags(ICVSFolder project) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		return root.getAllKnownTags(remotePath);
	}
	
	/*
	 * XXX I hope this methos is not needed in this form
	 */
	public Map getKnownProjectsAndVersions(ICVSRepositoryLocation location) {
		Map knownTags = new HashMap();
		RepositoryRoot root = getRepositoryRootFor(location);
		String[] paths = root.getKnownRemotePaths();
		for (int i = 0; i < paths.length; i++) {
			String path = paths[i];
			Set result = new HashSet();
			result.addAll(Arrays.asList(root.getAllKnownTags(path)));
			knownTags.put(path, result);
		}
		return knownTags;
	}
	
	public ICVSRemoteResource[] getFoldersForTag(ICVSRepositoryLocation location, CVSTag tag, IProgressMonitor monitor) throws CVSException {		
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask(Policy.bind("RepositoryManager.fetchingRemoteFolders", tag.getName()), 100); //$NON-NLS-1$
			if (tag.getType() == CVSTag.HEAD) {
				ICVSRemoteResource[] resources = location.members(tag, false, Policy.subMonitorFor(monitor, 60));
				RepositoryRoot root = getRepositoryRootFor(location);
				ICVSRemoteResource[] modules = root.getDefinedModules(tag, Policy.subMonitorFor(monitor, 40));
				ICVSRemoteResource[] result = new ICVSRemoteResource[resources.length + modules.length];
				System.arraycopy(resources, 0, result, 0, resources.length);
				System.arraycopy(modules, 0, result, resources.length, modules.length);
				return result;
			}
			if (tag.getType() == CVSTag.DATE) {
				ICVSRemoteResource[] resources = location.members(tag, false, Policy.subMonitorFor(monitor, 60));
				RepositoryRoot root = getRepositoryRootFor(location);
				ICVSRemoteResource[] modules = root.getDefinedModules(tag, Policy.subMonitorFor(monitor, 40));
				ICVSRemoteResource[] result = new ICVSRemoteResource[resources.length + modules.length];
				System.arraycopy(resources, 0, result, 0, resources.length);
				System.arraycopy(modules, 0, result, resources.length, modules.length);
				return result;
			}
			Set result = new HashSet();
			// Get the tags for the location
			RepositoryRoot root = getRepositoryRootFor(location);
			String[] paths = root.getKnownRemotePaths();
			for (int i = 0; i < paths.length; i++) {
				String path = paths[i];
				List tags = Arrays.asList(root.getAllKnownTags(path));
				if (tags.contains(tag)) {
					ICVSRemoteFolder remote = root.getRemoteFolder(path, tag, Policy.subMonitorFor(monitor, 100));
					result.add(remote);
				}
			}
			return (ICVSRemoteResource[])result.toArray(new ICVSRemoteResource[result.size()]);
		} finally {
			monitor.done();
		}
	}
		
	/*
	 * Fetches tags from auto-refresh files if they exist. Then fetches tags from the user defined auto-refresh file
	 * list. The fetched tags are cached in the CVS ui plugin's tag cache.
	 */
	public void refreshDefinedTags(ICVSFolder project, boolean replace, boolean notify, IProgressMonitor monitor) throws TeamException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		root.refreshDefinedTags(remotePath, replace, monitor);
		if (notify)
			broadcastRepositoryChange(root);
	}
	
	/**
	 * A repository root has been added. Notify any listeners.
	 */
	public void rootAdded(ICVSRepositoryLocation root) {
		Iterator it = listeners.iterator();
		while (it.hasNext()) {
			IRepositoryListener listener = (IRepositoryListener)it.next();
			listener.repositoryAdded(root);
		}
	}
	
	/**
	 * A repository root has been removed.
	 * Remove the tags defined for this root and notify any listeners
	 */
	public void rootRemoved(ICVSRepositoryLocation root) {
		RepositoryRoot repoRoot = (RepositoryRoot)repositoryRoots.remove(root.getLocation());
		if (repoRoot != null)
			broadcastRepositoryChange(repoRoot);
	}
	
	/**
	 * Accept tags for any CVS resource. However, for the time being,
	 * the given version tags are added to the list of known tags for the 
	 * remote ancestor of the resource that is a direct child of the remote root
	 */
	public void addTags(ICVSResource resource, CVSTag[] tags) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(resource);
		// XXX could be a file or folder
		String remotePath = RepositoryRoot.getRemotePathFor(resource);
		root.addTags(remotePath, tags);
		broadcastRepositoryChange(root);
	}
	public void addDateTag(ICVSRepositoryLocation location, CVSTag tag) {
		if(tag == null) return;
		RepositoryRoot root = getRepositoryRootFor(location);
		root.addDateTag(tag);
		broadcastRepositoryChange(root);
	}
	public void removeDateTag(ICVSRepositoryLocation location, CVSTag tag){
		RepositoryRoot root = getRepositoryRootFor(location);
		root.removeDateTag(tag);
		broadcastRepositoryChange(root);
	}
	public void setAutoRefreshFiles(ICVSFolder project, String[] filePaths) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		root.setAutoRefreshFiles(remotePath, filePaths);
	}
	
	public void removeAutoRefreshFiles(ICVSFolder project, String[] relativeFilePaths) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		Set set = new HashSet();
		set.addAll(Arrays.asList(root.getAutoRefreshFiles(remotePath)));
		set.removeAll(Arrays.asList(relativeFilePaths));
		root.setAutoRefreshFiles(remotePath, (String[]) set.toArray(new String[set.size()]));
	}
	
	public String[] getAutoRefreshFiles(ICVSFolder project) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		return root.getAutoRefreshFiles(remotePath);
	}
	
	/**
	 * Remove the given tags from the list of known tags for the
	 * given remote root.
	 */
	public void removeTags(ICVSFolder project, CVSTag[] tags) throws CVSException {
		RepositoryRoot root = getRepositoryRootFor(project);
		String remotePath = RepositoryRoot.getRemotePathFor(project);
		root.removeTags(remotePath, tags);
		broadcastRepositoryChange(root);
	}
	
	public void startup() {
		loadState();
		loadCommentHistory();
		CVSProviderPlugin.getPlugin().addRepositoryListener(new ICVSListener() {
			public void repositoryAdded(ICVSRepositoryLocation root) {
				rootAdded(root);
			}
			public void repositoryRemoved(ICVSRepositoryLocation root) {
				rootRemoved(root);
			}
		});
	}
	
	public void shutdown() throws TeamException {
		saveState();
		saveCommentHistory();
	}
	
	private void loadState() {
		IPath pluginStateLocation = CVSUIPlugin.getPlugin().getStateLocation().append(REPOSITORIES_VIEW_FILE);
		File file = pluginStateLocation.toFile();
		if (file.exists()) {
			try {
				BufferedInputStream is = new BufferedInputStream(new FileInputStream(file));
				try {
					readState(is);
				} finally {
					is.close();
				}
			} catch (IOException e) {
				CVSUIPlugin.log(Status.ERROR, Policy.bind("RepositoryManager.ioException"), e); //$NON-NLS-1$
			} catch (TeamException e) {
				CVSUIPlugin.log(e);
			}
		} else {
			IPath oldPluginStateLocation = CVSUIPlugin.getPlugin().getStateLocation().append(STATE_FILE);
			file = oldPluginStateLocation.toFile();
			if (file.exists()) {
				try {
					DataInputStream dis = new DataInputStream(new FileInputStream(file));
					try {
						readOldState(dis);
					} finally {
						dis.close();
					}
					saveState();
					file.delete();
				} catch (IOException e) {
					CVSUIPlugin.log(Status.ERROR, Policy.bind("RepositoryManager.ioException"), e); //$NON-NLS-1$
				} catch (TeamException e) {
					CVSUIPlugin.log(e);
				}
			} 
		}
	}
	private void loadCommentHistory() {
		IPath pluginStateLocation = CVSUIPlugin.getPlugin().getStateLocation().append(COMMENT_HIST_FILE);
		File file = pluginStateLocation.toFile();
		if (!file.exists()) return;
		try {
			BufferedInputStream is = new BufferedInputStream(new FileInputStream(file));
			try {
				readCommentHistory(is);
			} finally {
				is.close();
			}
		} catch (IOException e) {
			CVSUIPlugin.log(Status.ERROR, Policy.bind("RepositoryManager.ioException"), e); //$NON-NLS-1$
		} catch (TeamException e) {
			CVSUIPlugin.log(e);
		}
	}
	
	protected void saveState() throws TeamException {
		IPath pluginStateLocation = CVSUIPlugin.getPlugin().getStateLocation();
		File tempFile = pluginStateLocation.append(REPOSITORIES_VIEW_FILE + ".tmp").toFile(); //$NON-NLS-1$
		File stateFile = pluginStateLocation.append(REPOSITORIES_VIEW_FILE).toFile();
		try {
			XMLWriter writer = new XMLWriter(new BufferedOutputStream(new FileOutputStream(tempFile)));
			try {
				writeState(writer);
			} finally {
				writer.close();
			}
			if (stateFile.exists()) {
				stateFile.delete();
			}
			boolean renamed = tempFile.renameTo(stateFile);
			if (!renamed) {
				throw new TeamException(new Status(Status.ERROR, CVSUIPlugin.ID, TeamException.UNABLE, Policy.bind("RepositoryManager.rename", tempFile.getAbsolutePath()), null)); //$NON-NLS-1$
			}
		} catch (IOException e) {
			throw new TeamException(new Status(Status.ERROR, CVSUIPlugin.ID, TeamException.UNABLE, Policy.bind("RepositoryManager.save",stateFile.getAbsolutePath()), e)); //$NON-NLS-1$
		}
	}
	private void writeState(XMLWriter writer) {
		writer.startTag(RepositoriesViewContentHandler.REPOSITORIES_VIEW_TAG, null, true);
		// Write the repositories
		Collection repos = Arrays.asList(getKnownRepositoryLocations());
		Iterator it = repos.iterator();
		while (it.hasNext()) {
			CVSRepositoryLocation location = (CVSRepositoryLocation)it.next();
			RepositoryRoot root = getRepositoryRootFor(location);
			root.writeState(writer);
		}
		writer.endTag(RepositoriesViewContentHandler.REPOSITORIES_VIEW_TAG);
	}
	
	private void readState(InputStream stream) throws IOException, TeamException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			parser.parse(new InputSource(stream), new RepositoriesViewContentHandler(this));
		} catch (SAXException ex) {
			throw new CVSException(Policy.bind("RepositoryManager.parsingProblem", REPOSITORIES_VIEW_FILE), ex); //$NON-NLS-1$
		} catch (ParserConfigurationException ex) {
			throw new CVSException(Policy.bind("RepositoryManager.parsingProblem", REPOSITORIES_VIEW_FILE), ex); //$NON-NLS-1$
		}
	}
	private void readCommentHistory(InputStream stream) throws IOException, TeamException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			parser.parse(new InputSource(stream), new CommentHistoryContentHandler());
		} catch (SAXException ex) {
			throw new CVSException(Policy.bind("RepositoryManager.parsingProblem", COMMENT_HIST_FILE), ex); //$NON-NLS-1$
		} catch (ParserConfigurationException ex) {
			throw new CVSException(Policy.bind("RepositoryManager.parsingProblem", COMMENT_HIST_FILE), ex); //$NON-NLS-1$
		}
	}
	
	private void readOldState(DataInputStream dis) throws IOException, TeamException {
		int repoSize = dis.readInt();
		boolean version1 = false;
		if (repoSize == STATE_FILE_VERSION_1) {
			version1 = true;
			repoSize = dis.readInt();
		}
		for (int i = 0; i < repoSize; i++) {
			ICVSRepositoryLocation root = KnownRepositories.getInstance().getRepository(dis.readUTF());
			RepositoryRoot repoRoot = getRepositoryRootFor(root);
			
			// read branch tags associated with this root
			int tagsSize = dis.readInt();
			CVSTag[] branchTags = new CVSTag[tagsSize];
			for (int j = 0; j < tagsSize; j++) {
				String tagName = dis.readUTF();
				int tagType = dis.readInt();
				branchTags[j] = new CVSTag(tagName, tagType);
			}
			// Ignore the branch tags since they are handled differently now
			// addBranchTags(root, branchTags);
			
			// read the number of projects for this root that have version tags
			int projSize = dis.readInt();
			if (projSize > 0) {
				for (int j = 0; j < projSize; j++) {
					String name = dis.readUTF();
					Set tagSet = new HashSet();
					int numTags = dis.readInt();
					for (int k = 0; k < numTags; k++) {
						tagSet.add(new CVSTag(dis.readUTF(), CVSTag.VERSION));
					}
					CVSTag[] tags = (CVSTag[]) tagSet.toArray(new CVSTag[tagSet.size()]);
					repoRoot.addTags(name, tags);
				}
			}
			// read the auto refresh filenames for this project
			if (version1) {
				try {
					projSize = dis.readInt();
					if (projSize > 0) {
						for (int j = 0; j < projSize; j++) {
							String name = dis.readUTF();
							Set filenames = new HashSet();
							int numFilenames = dis.readInt();
							for (int k = 0; k < numFilenames; k++) {
								filenames.add(name + "/" + dis.readUTF()); //$NON-NLS-1$
							}
							repoRoot.setAutoRefreshFiles(name, (String[]) filenames.toArray(new String[filenames.size()]));
						}
					}
				} catch (EOFException e) {
					// auto refresh files are not persisted, continue and save them next time.
				}
			}
			broadcastRepositoryChange(repoRoot);
		}
	}
	
	protected void saveCommentHistory() throws TeamException {
		IPath pluginStateLocation = CVSUIPlugin.getPlugin().getStateLocation();
		File tempFile = pluginStateLocation.append(COMMENT_HIST_FILE + ".tmp").toFile(); //$NON-NLS-1$
		File histFile = pluginStateLocation.append(COMMENT_HIST_FILE).toFile();
		try {
				 XMLWriter writer = new XMLWriter(new BufferedOutputStream(new FileOutputStream(tempFile)));
		 		 try {
		 		 		 writeCommentHistory(writer);
		 		 } finally {
		 		 		 writer.close();
		 		 }
		 		 if (histFile.exists()) {
		 		 		 histFile.delete();
		 		 }
		 		 boolean renamed = tempFile.renameTo(histFile);
		 		 if (!renamed) {
		 		 		 throw new TeamException(new Status(Status.ERROR, CVSUIPlugin.ID, TeamException.UNABLE, Policy.bind("RepositoryManager.rename", tempFile.getAbsolutePath()), null)); //$NON-NLS-1$
		 		 }
		 } catch (IOException e) {
		 		 throw new TeamException(new Status(Status.ERROR, CVSUIPlugin.ID, TeamException.UNABLE, Policy.bind("RepositoryManager.save",histFile.getAbsolutePath()), e)); //$NON-NLS-1$
		 }
	}
	private void writeCommentHistory(XMLWriter writer) {
		writer.startTag(ELEMENT_COMMIT_HISTORY, null, false);
		for (int i=0; i<previousComments.length && i<MAX_COMMENTS; i++)
			writer.printSimpleTag(ELEMENT_COMMIT_COMMENT, previousComments[i]);
		writer.endTag(ELEMENT_COMMIT_HISTORY);
	}
		 
	public void addRepositoryListener(IRepositoryListener listener) {
		listeners.add(listener);
	}
	
	public void removeRepositoryListener(IRepositoryListener listener) {
		listeners.remove(listener);
	}
	
	/**
	 * Return the entered comment or null if canceled.
	 * @param proposedComment
	 */
	public String promptForComment(final Shell shell, IResource[] resourcesToCommit, String proposedComment) {
		final int[] result = new int[1];
		final ReleaseCommentDialog dialog = new ReleaseCommentDialog(shell, resourcesToCommit, proposedComment, IResource.DEPTH_INFINITE); 
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				result[0] = dialog.open();
				if (result[0] != ReleaseCommentDialog.OK) return;
			}
		});
		if (result[0] != ReleaseCommentDialog.OK) return null;
		return dialog.getComment();
	}
	
	/**
	 * Prompt to add all or some of the provided resources to version control.
	 * The value null is returned if the dialog is cancelled.
	 * 
	 * @param shell
	 * @param unadded
	 * @return IResource[]
	 */
	public IResource[] promptForResourcesToBeAdded(Shell shell, IResource[] unadded) {
		if (unadded == null) return new IResource[0];
		if (unadded.length == 0) return unadded;
		final IResource[][] result = new IResource[1][0];
		result[0] = null;
		final AddToVersionControlDialog dialog = new AddToVersionControlDialog(shell, unadded);
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				int code = dialog.open();
				if (code == IDialogConstants.YES_ID) {
					result[0] = dialog.getResourcesToAdd();
				} else if(code == IDialogConstants.NO_ID) {
					// allow the commit to continue.
					result[0] = new IResource[0];
				}
			}
		});
		return result[0];
	}
	
	public ICVSRepositoryLocation getRepositoryLocationFor(ICVSResource resource) {
		try {
			return internalGetRepositoryLocationFor(resource);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
			return null;
		}
	}

	private ICVSRepositoryLocation internalGetRepositoryLocationFor(ICVSResource resource) throws CVSException {
		ICVSFolder folder;
		if (resource.isFolder()) {
			folder = (ICVSFolder)resource;
		} else {
			folder = resource.getParent();
		}
		if (folder.isCVSFolder()) {
			ICVSRepositoryLocation location = KnownRepositories.getInstance().getRepository(folder.getFolderSyncInfo().getRoot());
			return location;
		}
		// XXX This is asking for trouble
		return null;
	}
		
	private RepositoryRoot getRepositoryRootFor(ICVSResource resource) throws CVSException {
		ICVSRepositoryLocation location = internalGetRepositoryLocationFor(resource);
		if (location == null) return null;
		return getRepositoryRootFor(location);
	}
	
	public RepositoryRoot getRepositoryRootFor(ICVSRepositoryLocation location) {
		RepositoryRoot root = (RepositoryRoot)repositoryRoots.get(location.getLocation());
		if (root == null) {
			root = new RepositoryRoot(location);
			add(root);
		}
		return root;
	}
	
	/**
	 * Add the given repository root to the receiver. The provided instance of RepositoryRoot
	 * is used to provide extra information about the repository location
	 * 
	 * @param currentRepositoryRoot
	 */
	public void add(RepositoryRoot root) {
		repositoryRoots.put(root.getRoot().getLocation(), root);
		broadcastRepositoryChange(root);
	}
	
	private void broadcastRepositoryChange(RepositoryRoot root) {
		if (notificationLevel == 0) {
			broadcastRepositoriesChanged(new ICVSRepositoryLocation[] {root.getRoot()});
		} else {
			changedRepositories.put(root.getRoot().getLocation(), root.getRoot());
		}
	}
	
	private void broadcastRepositoriesChanged(ICVSRepositoryLocation[] roots) {
		if (roots.length == 0) return;
		Iterator it = listeners.iterator();
		while (it.hasNext()) {
			IRepositoryListener listener = (IRepositoryListener)it.next();
			listener.repositoriesChanged(roots);
		}
	}
	
	/**
	 * Run the given runnable, waiting until the end to perform a refresh
	 * 
	 * @param runnable
	 * @param monitor
	 */
	public void run(IRunnableWithProgress runnable, IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		try {
			notificationLevel++;
			runnable.run(monitor);
		} finally {
			notificationLevel = Math.max(0, notificationLevel - 1);
			if (notificationLevel == 0) {
				try {
					Collection roots = changedRepositories.values();
					broadcastRepositoriesChanged((ICVSRepositoryLocation[]) roots.toArray(new ICVSRepositoryLocation[roots.size()]));
				} finally {
					changedRepositories.clear();
				}
			}
		}
	}
	
	/**
	 * Method isDisplayingProjectVersions.
	 * @param repository
	 * @return boolean
	 */
	public boolean isDisplayingProjectVersions(ICVSRepositoryLocation repository) {
		return true;
	}
	
	/**
	 * Method filterResources filters the given resources using the given
	 * working set.
	 *
	 * @param current
	 * @param resources
	 * @return ICVSRemoteResource[]
	 */
	public ICVSRemoteResource[] filterResources(IWorkingSet workingSet, ICVSRemoteResource[] resources) {
		if (workingSet == null) return resources;
		// get the projects associated with the working set
		IAdaptable[] adaptables = workingSet.getElements();
		Set projects = new HashSet();
		for (int i = 0; i < adaptables.length; i++) {
			IAdaptable adaptable = adaptables[i];
			Object adapted = adaptable.getAdapter(IResource.class);
			if (adapted != null) {
				// Can this code be generalized?
				IProject project = ((IResource)adapted).getProject();
				projects.add(project);
			}
		}
		List result = new ArrayList();
		for (int i = 0; i < resources.length; i++) {
			ICVSRemoteResource resource = resources[i];
			for (Iterator iter = projects.iterator(); iter.hasNext();) {
				IProject project = (IProject) iter.next();
				if (project.getName().equals(resource.getName())) {
					result.add(resource);
					break;
				}
			}
		}
		return (ICVSRemoteResource[]) result.toArray(new ICVSRemoteResource[result.size()]);
	}
	
	/**
	 * Method setLabel.
	 * @param location
	 * @param label
	 */
	public void setLabel(CVSRepositoryLocation location, String label) {
		RepositoryRoot root = getRepositoryRootFor(location);
		String oldLabel = root.getName();
		if (oldLabel == null) {
			if (label == null) return;
			root.setName(label);
		} else if (label == null) {
			root.setName(label);
		} else if (label.equals(oldLabel)) {
			return;
		} else {
			root.setName(label);
		}
		broadcastRepositoryChange(root);	
	}
	
	/**
	 * Replace the old repository location with the new one assuming that they
	 * are the same location with different authentication informations
	 * @param location
	 * @param newLocation
	 */
	public void replaceRepositoryLocation(
			final ICVSRepositoryLocation oldLocation,
			final CVSRepositoryLocation newLocation) {
		
		try {
			run(new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					RepositoryRoot root = getRepositoryRootFor(oldLocation);
					// Disposing of the old location will result in the deletion of the
					// cached root through a listener callback
					KnownRepositories.getInstance().disposeRepository(oldLocation);
					
					// Get the new location from the CVS plugin to ensure we use the
					// instance that will be returned by future calls to getRepository()
					boolean isNew = !KnownRepositories.getInstance().isKnownRepository(newLocation.getLocation());
					root.setRepositoryLocation(
							KnownRepositories.getInstance().addRepository(newLocation, isNew /* broadcast */));
					add(root);
				}
			}, Policy.monitorFor(null));
		} catch (InvocationTargetException e) {
			CVSException.wrapException(e);
		} catch (InterruptedException e) {
		}
	}
	
	/**
	 * Purge any cahced information.
	 */
	public void purgeCache() {
		for (Iterator iter = repositoryRoots.values().iterator(); iter.hasNext();) {
			RepositoryRoot root = (RepositoryRoot) iter.next();
			root.clearCache();
		}
	}

	/**
	 * Answer the list of comments that were previously used when committing.
	 * @return String[]
	 */
	public String[] getPreviousComments() {
		return previousComments;
	}

	/**
	 * Method addComment.
	 * @param string
	 */
	public void addComment(String comment) {
		// Only add the comment if its not there already
		if (containsComment(comment)) return;
		// Insert the comment as the first element
		String[] newComments = new String[Math.min(previousComments.length + 1, MAX_COMMENTS)];
		newComments[0] = comment;
		for (int i = 1; i < newComments.length; i++) {
			newComments[i] = previousComments[i-1];
		}
		previousComments = newComments;
	}

	private boolean containsComment(String comment) {
		for (int i = 0; i < previousComments.length; i++) {
			if (previousComments[i].equals(comment)) {
				return true;
			}
		}
		return false;
	}
}
