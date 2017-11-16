package org.concrete5.core.goals;

import java.util.List;

import org.concrete5.core.Common;
import org.concrete5.core.goals.evaluator.ProspectiveFactoryMethodCallTypeEvaluator;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.expressions.CallArgumentsList;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.SimpleReference;
import org.eclipse.dltk.ti.IGoalEvaluatorFactory;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.php.core.compiler.ast.nodes.Comment;
import org.eclipse.php.core.compiler.ast.nodes.FullyQualifiedReference;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.core.compiler.ast.nodes.PHPDocTag;
import org.eclipse.php.core.compiler.ast.nodes.Scalar;
import org.eclipse.php.core.compiler.ast.nodes.StaticConstantAccess;

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
		int flags = this.getMethodFlags(call);
		if (flags == ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_NONE) {
			return null;
		}
		String methodAgument = this.getMethodArgument(call);
		if (methodAgument == null) {
			return null;
		}
		return new ProspectiveFactoryMethodCallTypeEvaluator((ExpressionTypeGoal) goal, flags, methodAgument,
				expression.sourceStart());
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
	private int getMethodFlags(PHPCallExpression call) {
		SimpleReference methodNameReference = call.getCallName();
		if (methodNameReference == null) {
			return ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_NONE;
		}
		String methodName = methodNameReference.getName();
		if (methodName == null) {
			return ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_NONE;
		}
		if (methodName.equalsIgnoreCase("build")) { //$NON-NLS-1$
			return ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_CLASSES;
		}
		if (methodName.equalsIgnoreCase("make")) { //$NON-NLS-1$
			return ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_CLASSES
					| ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_ALIASES;
		}
		return ProspectiveFactoryMethodCallTypeEvaluator.FACTORYMETHOD_NONE;
	}

	/**
	 * Get the first argument (if it's a string).
	 *
	 * @param call
	 * @return
	 */
	private String getMethodArgument(PHPCallExpression call) {
		CallArgumentsList argumentsContainer = call.getArgs();
		List<ASTNode> arguments = argumentsContainer == null ? null : argumentsContainer.getChilds();
		if (arguments == null) {
			return null;
		}
		String firstArgument = null;
		for (int i = 0, n = arguments.size(); i < n; i++) {
			ASTNode node = arguments.get(i);
			if (node instanceof Comment) {
				continue;
			}
			if (node instanceof PHPDocTag) {
				continue;
			}
			if (node instanceof StaticConstantAccess) {
				firstArgument = this.getMethodArgument((StaticConstantAccess) node);
			} else if (node instanceof Scalar) {
				firstArgument = this.getMethodArgument((Scalar) node);
			}
			break;
		}
		return firstArgument;
	}

	/**
	 * Extract the class name from an argument like "ClassName::class"
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @return NULL if the argument is not a ..::class call, the class name
	 *         otherwise
	 */
	private String getMethodArgument(StaticConstantAccess argument) {
		if (!"class".equalsIgnoreCase(argument.getConstant().getName())) { //$NON-NLS-1$
			return null;
		}
		Expression dispatcher = argument.getDispatcher();
		if (!(dispatcher instanceof FullyQualifiedReference)) {
			return null;
		}
		FullyQualifiedReference fqr = (FullyQualifiedReference) dispatcher;
		String fqn = fqr.getFullyQualifiedName();
		if (fqn == null || fqn.length() == 0) {
			return null;
		}
		return fqn;
	}

	/**
	 * Extract the string contained in an argument and resolve it to a class name
	 * 
	 * @param argument
	 *            The argument to be parsed
	 * @param factoryMethodFlags
	 * @return NULL if the argument can't be resolved to a non-empty string
	 */
	@SuppressWarnings("restriction")
	private String getMethodArgument(Scalar argument) {
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
