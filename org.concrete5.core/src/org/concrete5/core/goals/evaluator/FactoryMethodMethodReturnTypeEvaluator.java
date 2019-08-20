package org.concrete5.core.goals.evaluator;

import java.util.ArrayList;
import java.util.List;

import org.concrete5.core.builder.ProjectDataFactory;
import org.concrete5.core.factory.FactoryMethod;
import org.concrete5.core.storage.FactoryMethodStorage;
import org.eclipse.core.resources.IProject;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ITypeHierarchy;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.evaluation.types.AmbiguousType;
import org.eclipse.dltk.evaluation.types.MultiTypeType;
import org.eclipse.dltk.ti.GoalState;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.ISourceModuleContext;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.dltk.ti.types.IEvaluatedType;
import org.eclipse.php.core.compiler.ast.nodes.NamespaceReference;
import org.eclipse.php.internal.core.typeinference.IModelAccessCache;
import org.eclipse.php.internal.core.typeinference.PHPClassType;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.eclipse.php.internal.core.typeinference.PHPSimpleTypes;
import org.eclipse.php.internal.core.typeinference.PHPTypeInferenceUtils;
import org.eclipse.php.internal.core.typeinference.context.IModelCacheContext;
import org.eclipse.php.internal.core.typeinference.goals.FactoryMethodMethodReturnTypeGoal;

@SuppressWarnings("restriction")
public class FactoryMethodMethodReturnTypeEvaluator extends GoalEvaluator {

	private boolean resultCalculated = false;
	private IEvaluatedType result = null;
	private FactoryMethodMethodReturnTypeGoal typedGoal;
	private List<FactoryMethod> prospectiveFactoryMethods;
	private ISourceModuleContext sourceModuleContext;
	private IModelAccessCache contextCache;

	public FactoryMethodMethodReturnTypeEvaluator(FactoryMethodMethodReturnTypeGoal goal) {
		super(goal);
	}

	@Override
	public IGoal[] init() {
		this.result = null;
		this.resultCalculated = false;
		return IGoal.NO_GOALS;
	}

	@Override
	public IGoal[] subGoalDone(IGoal subgoal, Object result, GoalState state) {
		return IGoal.NO_GOALS;
	}

	@Override
	public Object produceResult() {
		if (this.resultCalculated == false) {
			this.result = this.calculateReult();
			this.resultCalculated = true;
		}
		return this.result;
	}

	private IEvaluatedType calculateReult() {
		this.typedGoal = (FactoryMethodMethodReturnTypeGoal) this.goal;
		IContext context = goal.getContext();
		this.sourceModuleContext = (context instanceof ISourceModuleContext) ? (ISourceModuleContext) context : null;
		this.contextCache = (context instanceof IModelCacheContext) ? ((IModelCacheContext) context).getCache() : null;
		this.prospectiveFactoryMethods = this.getProspectiveFactoryMethods();
		if (prospectiveFactoryMethods == null) {
			return null;
		}
		FactoryMethod factoryMethod = null;
		IEvaluatedType evaluatedType = this.typedGoal.getEvaluatedType();
		if (evaluatedType instanceof PHPClassType) {
			factoryMethod = this.getFactoryMethod((PHPClassType) evaluatedType);
		} else {
			IType[] types = this.typedGoal.getTypes();
			if (types != null) {
				for (IType type : types) {
					factoryMethod = this.getFactoryMethod(type);
					if (factoryMethod != null) {
						break;
					}
				}
			}
			if (factoryMethod == null && evaluatedType == null && types == null) {
				factoryMethod = this.getFactoryMethod("");
			}
		}
		if (factoryMethod == null) {
			return null;
		}
		String[] methodArguments = this.typedGoal.getArgNames();
		String methodArgument = null;
		if (methodArguments != null) {
			if (factoryMethod.discrimintatorIndex < methodArguments.length) {
				methodArgument = methodArguments[factoryMethod.discrimintatorIndex];
			}
		}
		String alias;
		if (methodArgument == null || methodArgument.length() == 0
				|| !factoryMethod.aliases.containsKey(methodArgument)) {
			alias = factoryMethod.fallbackAlias;
		} else {
			alias = factoryMethod.aliases.get(methodArgument);
		}
		if (alias == null || alias.isEmpty()) {
			return null;
		}
		String[] aliases = alias.split("\\|");
		return this.resolveAlias(aliases, methodArgument);
	}

	/**
	 * Get the prospective behavior of a factory method call.
	 */
	private List<FactoryMethod> getProspectiveFactoryMethods() {
		String methodName = this.typedGoal.getMethodName();
		if (methodName == null || methodName.isEmpty()) {
			return null;
		}
		if (methodName.charAt(0) == NamespaceReference.NAMESPACE_SEPARATOR) {
			methodName = methodName.substring(1);
			if (methodName.isEmpty()) {
				return null;
			}
		}
		if (this.sourceModuleContext == null) {
			return null;
		}
		IScriptProject scriptProject = this.sourceModuleContext.getSourceModule().getScriptProject();
		if (scriptProject == null) {
			return null;
		}
		IProject project = scriptProject.getProject();
		if (project == null) {
			return null;
		}
		FactoryMethodStorage fmStorage = ProjectDataFactory.get(project).getFactoryMethodStorage();
		List<FactoryMethod> result = fmStorage.getFactoryMethodsByMethodName(methodName,
				FactoryMethod.TYPE_INSTANCEMETHOD);
		return result.size() == 0 ? null : result;
	}

	private FactoryMethod getFactoryMethod(String className) {
		if (className == null || className.isEmpty()) {
			for (FactoryMethod fm : this.prospectiveFactoryMethods) {
				if (fm.className.isEmpty()) {
					return fm;
				}
			}
		} else {
			for (FactoryMethod fm : this.prospectiveFactoryMethods) {
				if (fm.className.equalsIgnoreCase(className)) {
					return fm;
				}
			}
		}
		return null;
	}

	private FactoryMethod getFactoryMethod(PHPClassType classType) {
		if (classType == null) {
			return null;
		}
		String className = classType.getTypeName();
		FactoryMethod factoryMethod = null;
		if (className != null && className.length() >= 2
				&& className.charAt(0) != NamespaceReference.NAMESPACE_SEPARATOR) {
			factoryMethod = this.getFactoryMethod(className.substring(1));
			if (factoryMethod != null) {
				return factoryMethod;
			}
		}
		if (this.sourceModuleContext != null) {
			IType[] types = PHPTypeInferenceUtils.getModelElements(classType, this.sourceModuleContext, 0,
					this.contextCache);
			if (types != null) {
				for (IType type : types) {
					factoryMethod = this.getFactoryMethod(type);
					if (factoryMethod != null) {
						return factoryMethod;
					}
				}
			}
		}
		return null;
	}

	private FactoryMethod getFactoryMethod(IType classType) {
		if (classType == null) {
			return null;
		}
		String className = classType.getTypeQualifiedName(NamespaceReference.NAMESPACE_DELIMITER);
		if (className == null || className.isEmpty()) {
			return null;
		}
		FactoryMethod factoryMethod = this.getFactoryMethod(className);
		if (factoryMethod != null) {
			return factoryMethod;
		}
		ITypeHierarchy hierarchy = null;
		if (this.contextCache != null) {
			try {
				hierarchy = this.contextCache.getSuperTypeHierarchy(classType, null);
			} catch (ModelException e) {

			}
		}
		IType[] superClasses;
		try {
			superClasses = PHPModelUtils.getSuperClasses(classType, hierarchy);
		} catch (ModelException e) {
			superClasses = null;
		}
		if (superClasses != null) {
			for (IType superClass : superClasses) {
				factoryMethod = this.getFactoryMethod(superClass);
				if (factoryMethod != null) {
					return factoryMethod;
				}
			}
		}

		return null;
	}

	private IEvaluatedType resolveAlias(String[] aliases, String methodArgument) {
		if (aliases == null || aliases.length == 0) {
			return null;
		}
		ArrayList<IEvaluatedType> types = new ArrayList<IEvaluatedType>(aliases.length);
		for (String alias : aliases) {
			IEvaluatedType type = this.resolveAlias(alias, methodArgument);
			if (type != null) {
				types.add(type);
			}
		}
		int numTypes = types.size();
		switch (numTypes) {
		case 0:
			return null;
		case 1:
			return types.get(0);
		default:
			IEvaluatedType[] typeArray = new IEvaluatedType[numTypes];
			for (int typeIndex = 0; typeIndex < numTypes; typeIndex++) {
				typeArray[typeIndex] = types.get(typeIndex);
			}
			return new AmbiguousType(typeArray);
		}
	}

	private IEvaluatedType resolveAlias(String alias, String methodArgument) {
		int len = alias.length();
		if (alias.isEmpty()) {
			return null;
		}
		boolean isArray = len >= 2 && alias.endsWith("[]");
		if (isArray) {
			alias = alias.substring(0, len - 2);
			if (alias.isEmpty()) {
				return null;
			}
		}
		String resultingClass = null;
		if (alias.equals("@")) {
			if (methodArgument == null || methodArgument.isEmpty()) {
				return null;
			}
			resultingClass = methodArgument;
		} else {
			resultingClass = alias;
		}
		IEvaluatedType type = PHPSimpleTypes.fromString(resultingClass);
		if (type == null) {
			if (resultingClass.charAt(0) != NamespaceReference.NAMESPACE_SEPARATOR) {
				resultingClass = NamespaceReference.NAMESPACE_DELIMITER.concat(resultingClass);
			}
			type = new PHPClassType(resultingClass);
		}
		if (!isArray) {
			return type;
		}
		MultiTypeType arrayType = new MultiTypeType();
		arrayType.addType(type);

		return arrayType;
	}
}
