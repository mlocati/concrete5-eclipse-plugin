package org.concrete5.core;

import org.concrete5.core.builder.ProjectDataFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Concrete5CorePlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.concrete5.core"; //$NON-NLS-1$

	// The shared instance
	private static Concrete5CorePlugin plugin;

	private static IResourceChangeListener resourceChangeListener = null;

	private final static IResourceDeltaVisitor resourceDeltaVisitor = new IResourceDeltaVisitor() {
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta == null ? null : delta.getResource();
			if (resource == null) {
				return false;
			}
			int resourceType = resource.getType();
			if ((resourceType & IResource.ROOT) != 0) {
				return true;
			}
			if ((resourceType & IResource.PROJECT) != 0) {
				int flags = delta.getFlags();
				if ((flags & IResourceDelta.OPEN) != 0) {
					IProject project = (IProject) resource;
					if (project.isOpen()) {
						ProjectDataFactory.get(project);
					} else {
						releaseProject(project, false);
					}
					return false;
				}
				int kind = delta.getKind();
				if ((kind & IResourceDelta.REMOVED) != 0) {
					releaseProject((IProject) resource, true);
					return false;
				}
				if ((flags & IResourceDelta.DESCRIPTION) != 0) {
					projectDescriptionUpdated((IProject) resource);
					return false;
				}
				return true;
			}
			if ((resourceType & IResource.FILE) != 0) {
				int kind = delta.getKind();
				if ((kind & IResourceDelta.REMOVED) != 0) {
					IPath resourcePath = resource.getFullPath();
					if (resourcePath == null) {
						return false;
					}
					String resourcePathString = resourcePath.toPortableString();
					IProject project = (IProject) resource.getProject();
					if (project == null) {
						return false;
					}
					ProjectDataFactory.get(project).getFactoryMethodStorage().resetForPath(resourcePathString);
					return true;
				}
				int flags = delta.getFlags();
				if ((flags & IResourceDelta.MOVED_TO) != 0) {
					IPath oldPath = resource.getFullPath();
					if (oldPath == null) {
						return false;
					}
					IPath newPath = delta.getMovedToPath();
					if (newPath == null) {
						return false;
					}
					String oldPathString = oldPath.toPortableString();
					String newPathString = newPath.toPortableString();
					if (oldPathString.equals(newPathString)) {
						return false;
					}
					IProject project = (IProject) resource.getProject();
					if (project == null) {
						return false;
					}
					ProjectDataFactory.get(project).getFactoryMethodStorage().renameForPath(oldPathString,
							newPathString);
					return false;
				}
				return false;
			}
			return true;
		}
	};

	/**
	 * The constructor
	 */
	public Concrete5CorePlugin() {
	}

	public static void initializeAfterLoad(IProgressMonitor monitor) throws CoreException {
		System.out.println("ciao");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.
	 * BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		hookResourceChanges();
		checkOpenProjects();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		unhookResourceChanges();
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Concrete5CorePlugin getDefault() {
		return plugin;
	}

	private static IWorkspace getWorkspace() {
		try {
			return ResourcesPlugin.getWorkspace();
		} catch (IllegalStateException x) {
			return null;
		}
	}

	private static void checkOpenProjects() {
		IWorkspace workspace = getWorkspace();
		if (workspace == null) {
			return;
		}
		IWorkspaceRoot root = workspace.getRoot();
		if (root == null) {
			return;
		}
		IProject[] projects = root.getProjects();
		if (projects == null) {
			return;
		}
		for (IProject project : projects) {
			if (project != null && project.isAccessible()) {
				ProjectDataFactory.get(project);
			}
		}

	}

	private static void unhookResourceChanges() {
		if (resourceChangeListener == null) {
			return;
		}
		IWorkspace workspace = getWorkspace();
		if (workspace == null) {
			return;
		}
		workspace.removeResourceChangeListener(resourceChangeListener);
		resourceChangeListener = null;
	}

	private static void releaseProject(IProject project, boolean deleted) {
		if (deleted) {
			ProjectDataFactory.get(project).deleteData();
		}
		ProjectDataFactory.release(project);
	}

	private static void projectDescriptionUpdated(IProject project) {
		ProjectDataFactory.descriptionUpdated(project);
	}

	private static void hookResourceChanges() {
		if (resourceChangeListener != null) {
			return;
		}
		IWorkspace workspace = getWorkspace();
		if (workspace == null) {
			return;
		}
		resourceChangeListener = new IResourceChangeListener() {
			public void resourceChanged(final IResourceChangeEvent event) {
				if (event == null) {
					return;
				}
				IResourceDelta delta = event.getDelta();
				if (delta == null) {
					return;
				}
				try {
					delta.accept(resourceDeltaVisitor);
				} catch (CoreException x) {
				}
			};
		};
		workspace.addResourceChangeListener(resourceChangeListener);
	}
}
