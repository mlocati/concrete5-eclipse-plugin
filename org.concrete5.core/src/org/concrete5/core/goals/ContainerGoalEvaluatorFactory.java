package org.concrete5.core.goals;

import java.util.List;

import org.concrete5.core.Common;
import org.concrete5.core.factory.FactoryMethod;
import org.concrete5.core.goals.evaluator.ProspectiveFactoryMethodCallTypeEvaluator;
import org.concrete5.core.storage.FactoryMethodStorage;
import org.concrete5.core.storage.FactoryMethodStorageFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.expressions.CallArgumentsList;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.ConstantReference;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.internal.core.SourceType;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.IGoalEvaluatorFactory;
import org.eclipse.dltk.ti.ISourceModuleContext;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.php.core.compiler.ast.nodes.FullyQualifiedReference;
import org.eclipse.php.core.compiler.ast.nodes.NamespaceReference;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.core.compiler.ast.nodes.Scalar;
import org.eclipse.php.core.compiler.ast.nodes.StaticConstantAccess;
import org.eclipse.php.internal.core.typeinference.IModelAccessCache;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.eclipse.php.internal.core.typeinference.context.IModelCacheContext;

@SuppressWarnings("restriction")
public class ContainerGoalEvaluatorFactory implements IGoalEvaluatorFactory {

	@Override
	public GoalEvaluator createEvaluator(IGoal goal) {
		if (this.checkNature(goal) == false) {
			return null;
		}
		if (!(goal instanceof ExpressionTypeGoal)) {
			return null;
		}
		ASTNode expression = ((ExpressionTypeGoal) goal).getExpression();
		if (!(expression instanceof PHPCallExpression)) {
			return null;
		}
		PHPCallExpression call = (PHPCallExpression) expression;
		List<FactoryMethod> prospectiveFactoryMethods = this.getProspectiveFactoryMethods(goal.getContext(), call);
		if (prospectiveFactoryMethods == null) {
			return null;
		}
		String[] stringArguments = this.getMethodStringArguments(call, goal.getContext());
		if (stringArguments == null) {
			return null;
		}
		return new ProspectiveFactoryMethodCallTypeEvaluator((ExpressionTypeGoal) goal, prospectiveFactoryMethods,
				stringArguments, expression.sourceStart());
	}

	/**
	 * Does the goal is associated to a concrete5 project?
	 *
	 * @param goal
	 *            The goal to check.
	 * @return true if the goal is associated to a concret5 project, false
	 *         otherwise.
	 */
	private boolean checkNature(IGoal goal) {
		return goal != null && Common.hasConcrete5Nature(goal.getContext());
	}

	/**
	 * Get the prospective behavior of a factory method call.
	 * 
	 * @param call
	 */
	private List<FactoryMethod> getProspectiveFactoryMethods(IContext context, PHPCallExpression call) {
		SimpleReference methodNameReference = call.getCallName();
		if (methodNameReference == null) {
			return null;
		}
		String methodName = methodNameReference.getName();
		if (methodName == null || methodName.isEmpty()) {
			return null;
		}
		if (!(context instanceof ISourceModuleContext)) {
			return null;
		}
		ISourceModule sourceModule = ((ISourceModuleContext) context).getSourceModule();
		if (sourceModule == null) {
			return null;
		}
		IScriptProject scriptProject = sourceModule.getScriptProject();
		if (scriptProject == null) {
			return null;
		}
		IProject project = scriptProject.getProject();
		if (project == null) {
			return null;
		}
		FactoryMethodStorage fmStorage = FactoryMethodStorageFactory.getForProject(project);
		List<FactoryMethod> result = fmStorage.getFactoryMethodsByMethodName(methodName,
				FactoryMethod.TYPE_INSTANCEMETHOD);
		return result.size() == 0 ? null : result;
	}

	/**
	 * Get the first argument (if it's a string).
	 *
	 * @param call
	 * @return
	 */
	private String[] getMethodStringArguments(PHPCallExpression call, IContext context) {
		CallArgumentsList argumentsContainer = call.getArgs();
		List<ASTNode> arguments = argumentsContainer == null ? null : argumentsContainer.getChilds();
		int numArguments = arguments == null ? null : arguments.size();
		if (numArguments == 0) {
			return null;
		}
		String[] stringArguments = new String[numArguments];
		boolean someFound = false;
		for (int index = 0; index < numArguments; index++) {
			ASTNode argumentNode = arguments.get(index);
			if (argumentNode instanceof Scalar) {
				stringArguments[index] = this.getMethodArgument((Scalar) argumentNode, context);
			} else if (argumentNode instanceof StaticConstantAccess) {
				stringArguments[index] = this.getMethodArgument((StaticConstantAccess) argumentNode, context);
			}
			someFound = someFound || stringArguments[index] != null;
		}
		return someFound ? stringArguments : null;
	}

	/**
	 * Extract the class name from an argument like "ClassName::class"
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @return NULL if the argument is not a ..::class call, the class name
	 *         otherwise
	 */
	private String getMethodArgument(StaticConstantAccess argument, IContext context) {
		ConstantReference constantReference = argument.getConstant();
		if (constantReference == null) {
			return null;
		}
		String constantName = constantReference.getName();
		if (constantName == null || !"class".equalsIgnoreCase(constantName)) { //$NON-NLS-1$
			return null;
		}
		Expression dispatcher = argument.getDispatcher();
		if (!(dispatcher instanceof FullyQualifiedReference)) {
			return null;
		}
		String localClassName = ((FullyQualifiedReference) dispatcher).getFullyQualifiedName();
		if (localClassName == null || localClassName.length() == 0) {
			return null;
		}
		if (!(context instanceof ISourceModuleContext)) {
			return null;
		}
		ISourceModule sourceModule = ((ISourceModuleContext) context).getSourceModule();
		IModelAccessCache cache = (context instanceof IModelCacheContext) ? ((IModelCacheContext) context).getCache() : null;
		IType[] types;
		try {
			types = PHPModelUtils.getTypes(/* typeName */ localClassName, /* sourceModule */sourceModule, /* offset */argument.sourceStart(), /* cache */ cache, /* monitor */ null, /* isType */true, /* isGlobal */ false);
		} catch (ModelException $x) {
			types = null;
		}
		if (types == null || types.length != 1 || !(types[0] instanceof SourceType)) {
			return null;
		}
		String fullyQualifiedName = ((SourceType) types[0]).getTypeQualifiedName(NamespaceReference.NAMESPACE_DELIMITER);
		if (fullyQualifiedName == null || fullyQualifiedName.length() == 0) {
			return null;
		}
		if (fullyQualifiedName.charAt(0) == NamespaceReference.NAMESPACE_SEPARATOR) {
			if (fullyQualifiedName.length() == 1) {
				return null;
			}
			fullyQualifiedName = fullyQualifiedName.substring(1);
		}
		return fullyQualifiedName;
	}

	/**
	 * Extract the string contained in an argument and resolve it to a class name
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @param factoryMethodFlags
	 * @return NULL if the argument can't be resolved to a non-empty string
	 */
	private String getMethodArgument(Scalar argument, IContext context) {
		if (argument.getScalarType() != Scalar.TYPE_STRING) {
			return null;
		}
		String value = argument.getValue();
		if (value == null || value.length() == 0) {
			return null;
		}

		value = org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils.stripQuotes(value);
		return value.length() == 0 ? null : value;
	}
}
