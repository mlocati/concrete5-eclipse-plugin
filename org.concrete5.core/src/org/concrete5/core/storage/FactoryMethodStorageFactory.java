package org.concrete5.core.storage;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

public class FactoryMethodStorageFactory {
	private static ILock instanceLock = Job.getJobManager().newLock();
	private static IResourceChangeListener resourceChangeListener = null;
	private static Map<IProject, FactoryMethodStorage> projectStorages = null;

	public static FactoryMethodStorage getForProject(IProject project) {
		FactoryMethodStorageFactory.instanceLock.acquire();
		try {
			if (projectStorages == null) {
				projectStorages = new HashMap<IProject, FactoryMethodStorage>();
			}
			FactoryMethodStorage result = FactoryMethodStorageFactory.projectStorages.get(project);
			if (result != null) {
				return result;
			}
			FactoryMethodStorageFactory.hookResourceChanges();
			result = new FactoryMethodStorage(project);
			FactoryMethodStorageFactory.projectStorages.put(project, result);
			return result;
		} finally {
			FactoryMethodStorageFactory.instanceLock.release();
		}
	}

	private static void projectClosed(IProject project) {
		FactoryMethodStorageFactory.instanceLock.acquire();
		try {
			if (FactoryMethodStorageFactory.projectStorages == null) {
				return;
			}
			FactoryMethodStorage storage = FactoryMethodStorageFactory.projectStorages.get(project);
			if (storage == null) {
				return;
			}
			FactoryMethodStorageFactory.projectStorages.remove(project);
			storage.dispose();
		} finally {
			FactoryMethodStorageFactory.instanceLock.release();
		}
	}
	
	private static void projectChanged(IProject project) {
		FactoryMethodStorageFactory.instanceLock.acquire();
		try {
			if (FactoryMethodStorageFactory.projectStorages == null) {
				return;
			}
			FactoryMethodStorage storage = FactoryMethodStorageFactory.projectStorages.get(project);
			if (storage == null) {
				return;
			}
			storage.projectChanged();
		} finally {
			FactoryMethodStorageFactory.instanceLock.release();
		}
	}

	private static void hookResourceChanges() {
		if (FactoryMethodStorageFactory.resourceChangeListener != null) {
			return;
		}
		IWorkspace workspace;
		try {
			workspace = ResourcesPlugin.getWorkspace();
		} catch (IllegalStateException x) {
			workspace = null;
		}
		if (workspace == null) {
			return;
		}
		FactoryMethodStorageFactory.resourceChangeListener = new IResourceChangeListener() {
			public void resourceChanged(final IResourceChangeEvent event) {
				IResourceDelta delta = event == null ? null : event.getDelta();
				if (delta == null) {
					return;
				}
				try {
					delta.accept(new IResourceDeltaVisitor() {
						public boolean visit(IResourceDelta delta) throws CoreException {
							IResource resource = delta == null ? null : delta.getResource();
							if (resource == null) {
								return true;
							}
							if ((resource.getType() & IResource.PROJECT) == 0) {
								return true;
							}
							int flags = delta.getFlags();
							if ((flags & IResourceDelta.OPEN) != 0) {
								IProject project = (IProject) resource;
								if (!project.isOpen()) {
									FactoryMethodStorageFactory.projectClosed(project);
								}
							}
							if ((flags & IResourceDelta.REMOVED) != 0) {
								FactoryMethodStorageFactory.projectClosed((IProject) resource);
							}
							if ((flags & IResourceDelta.DESCRIPTION) != 0) {
								FactoryMethodStorageFactory.projectChanged((IProject) resource);
							}
							return true;
						}
					});
				} catch (CoreException x) {
				}
			};
		};
		workspace.addResourceChangeListener(FactoryMethodStorageFactory.resourceChangeListener);
	}
}
