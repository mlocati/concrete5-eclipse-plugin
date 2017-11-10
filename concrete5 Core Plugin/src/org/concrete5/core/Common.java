package org.concrete5.core;

import org.concrete5.core.builder.Concrete5Nature;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.ISourceModuleContext;

public class Common {

	/**
	 * Cast an object to a specific class, if possible.
	 *
	 * @param obj
	 *            The object to cast
	 * @param cls
	 *            The class you want
	 *
	 * @return Return null if the cast is not possible, the casted object otherwise.
	 */
	public static <T> T as(Object obj, Class<T> cls) {
		return cls.isInstance(obj) ? cls.cast(obj) : null;
	}

	/**
	 * Check if a project has the concrete5 nature.
	 *
	 * @param project
	 *            The project to be checked
	 * @return Return true if the project has a concrete5 nature, false otherwise
	 */
	public static boolean hasConcrete5Nature(IProject project) {
		if (project == null) {
			return false;
		}
		IProjectNature nature;
		try {
			nature = project.getNature(Concrete5Nature.NATURE_ID);
		} catch (CoreException e) {
			nature = null;
		}
		return nature != null && nature instanceof Concrete5Nature;
	}

	/**
	 * Check if a script project has the concrete5 nature.
	 *
	 * @param scriptProject
	 *            The script project to be checked
	 * @return Return true if the script project has a concrete5 nature, false
	 *         otherwise
	 */
	public static boolean hasConcrete5Nature(IScriptProject scriptProject) {
		return scriptProject != null && hasConcrete5Nature(scriptProject.getProject());
	}

	/**
	 * Check if a source module has the concrete5 nature.
	 *
	 * @param sourceModule
	 *            The source module to be checked
	 * @return Return true if the source module has a concrete5 nature, false
	 *         otherwise
	 */
	public static boolean hasConcrete5Nature(ISourceModule sourceModule) {
		return sourceModule != null && hasConcrete5Nature(sourceModule.getScriptProject());
	}

	/**
	 * Check if a source module context has the concrete5 nature.
	 *
	 * @param sourceModuleContext
	 *            The source module context to be checked
	 * @return Return true if the source module context has a concrete5 nature,
	 *         false otherwise
	 */
	public static boolean hasConcrete5Nature(ISourceModuleContext sourceModuleContext) {
		return sourceModuleContext != null && hasConcrete5Nature(sourceModuleContext.getSourceModule());
	}

	/**
	 * Check if a context has the concrete5 nature.
	 *
	 * @param context
	 *            The context to be checked
	 * @return Return true if the context has a concrete5 nature, false otherwise
	 */
	public static boolean hasConcrete5Nature(IContext context) {
		return context != null && hasConcrete5Nature(as(context, ISourceModuleContext.class));
	}
}
