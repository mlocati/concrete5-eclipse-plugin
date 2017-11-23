package org.concrete5.core.builder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

public class ProjectDataFactory {

	private final static ILock projectContainersLock = Job.getJobManager().newLock();
	private final static Map<IProject, ProjectData> projectDatas = new HashMap<IProject, ProjectData>();

	public static ProjectData get(IProject project) {
		projectContainersLock.acquire();
		try {
			if (!projectDatas.containsKey(project)) {
				projectDatas.put(project, new ProjectData(project));
			}
			return projectDatas.get(project);
		} finally {
			projectContainersLock.release();
		}
	}

	public static void release(IProject project) {
		projectContainersLock.acquire();
		try {
			if (projectDatas.containsKey(project)) {
				projectDatas.remove(project);
			}
		} finally {
			projectContainersLock.release();
		}
	}

	public static void descriptionUpdated(IProject project) {
		projectContainersLock.acquire();
		try {
			if (projectDatas.containsKey(project)) {
				projectDatas.get(project).descriptionUpdated();
			}
		} finally {
			projectContainersLock.release();
		}
	}
}
