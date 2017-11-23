package org.concrete5.core.facet;

import org.concrete5.core.builder.Concrete5Nature;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.common.project.facet.core.IDelegate;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;

public class InstallActionDelegate implements IDelegate {

	@Override
	public void execute(IProject project, IProjectFacetVersion version, Object object, IProgressMonitor monitor)
			throws CoreException {

		monitor.subTask("Installing concrete5 nature");

		IProjectDescription description = project.getDescription();
		if (description != null) {
			String[] natures = description.getNatureIds();
			if (natures != null && natures.length > 0) {
				boolean hasPhpNature = false;
				boolean hasConcrete5Nature = false;
				for (int i = 0; i < natures.length; i++) {
					if (natures[i] != null & natures[i].endsWith(".PHPNature")) {
						hasPhpNature = true;
					} else if (natures[i].equals(Concrete5Nature.NATURE_ID)) {
						hasConcrete5Nature = true;
					}
				}
				if (!hasConcrete5Nature && hasPhpNature) {
					String[] newNatures = new String[natures.length + 1];
					System.arraycopy(natures, 0, newNatures, 0, natures.length);
					newNatures[natures.length] = Concrete5Nature.NATURE_ID;
					description.setNatureIds(newNatures);
					project.setDescription(description, monitor);
				}
			}
		}
	}
}
