package org.concrete5.core.builder;

import java.io.File;
import java.io.FileFilter;

import org.concrete5.core.Concrete5CorePlugin;
import org.concrete5.core.storage.FactoryMethodStorage;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

public class ProjectData {

	private final IProject project;

	private final ILock factoryMethodStorageLock = Job.getJobManager().newLock();
	private FactoryMethodStorage factoryMethodStorage = null;
	private Boolean concrete5NatureFlag = null;
	private IPath dataPath = null;

	public ProjectData(IProject project) {
		this.project = project;
	}

	public void descriptionUpdated() {
		this.concrete5NatureFlag = null;
	}

	public FactoryMethodStorage getFactoryMethodStorage() {
		this.factoryMethodStorageLock.acquire();
		try {
			if (this.factoryMethodStorage == null) {
				this.factoryMethodStorage = new FactoryMethodStorage(this);
			}
			return this.factoryMethodStorage;
		} finally {
			this.factoryMethodStorageLock.release();
		}

	}

	public void deleteData() {
		this.getFactoryMethodStorage().removeAllFactoryMethods();
		try {
			File directory = this.getDataPath().toFile();
			if (directory.isDirectory()) {
				// Delete files
				for (File file : directory.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile();
					}
				})) {
					file.delete();
				}
				// Delete everything else
				for (File file : directory.listFiles()) {
					file.delete();
				}
				directory.delete();
			}
		} catch (Throwable x) {
		}
	}

	public boolean hasConcrete5Nature() {
		if (this.concrete5NatureFlag == null) {
			IProjectNature nature;
			try {
				nature = project.getNature(Concrete5Nature.NATURE_ID);
			} catch (CoreException e) {
				nature = null;
			}
			this.concrete5NatureFlag = new Boolean(nature != null && nature instanceof Concrete5Nature);
		}
		return this.concrete5NatureFlag.booleanValue();
	}

	public IPath getDataPath() {
		if (this.dataPath != null) {
			return this.dataPath;
		}
		Concrete5CorePlugin plugin = Concrete5CorePlugin.getDefault();
		IPath stateLocation = plugin.getStateLocation();
		this.dataPath = stateLocation.append(this.project.getFullPath());
		return this.dataPath;
	}
}
