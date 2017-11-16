package org.concrete5.core.facet;

import org.concrete5.core.builder.Concrete5Nature;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.common.project.facet.core.IDelegate;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;

public class UninstallActionDelegate implements IDelegate {

	@Override
	public void execute(IProject project, IProjectFacetVersion version, Object object, IProgressMonitor monitor)
			throws CoreException {

		monitor.subTask("Uninstalling concrete5 nature");
		IProjectDescription description = project.getDescription();
		if (description != null) {
			String[] natures = description.getNatureIds();
			if (natures != null && natures.length > 0) {
				String[] newNatures = new String[natures.length - 1];
				boolean natureFound = false;
				int newNatureIndex = 0;
				for (int natureIndex = 0; natureIndex < natures.length; natureIndex++) {
					if (natures[natureIndex].equals(Concrete5Nature.NATURE_ID)) {
						natureFound = true;
					} else if (newNatureIndex < newNatures.length) {
						newNatures[newNatureIndex] = natures[0];
						newNatureIndex++;
					}
				}
				if (natureFound) {
					description.setNatureIds(newNatures);
					project.setDescription(description, monitor);
				}
			}
		}
	}

}
