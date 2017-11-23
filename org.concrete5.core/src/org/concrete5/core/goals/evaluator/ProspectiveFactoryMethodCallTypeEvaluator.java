package org.concrete5.core.goals.evaluator;

import java.util.List;

import org.concrete5.core.factory.FactoryMethod;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.core.IType;
import org.eclipse.dltk.core.ITypeHierarchy;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.ti.GoalState;
import org.eclipse.dltk.ti.IContext;
import org.eclipse.dltk.ti.ISourceModuleContext;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.dltk.ti.types.IEvaluatedType;
import org.eclipse.php.core.compiler.ast.nodes.NamespaceReference;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.internal.core.typeinference.IModelAccessCache;
import org.eclipse.php.internal.core.typeinference.PHPClassType;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.eclipse.php.internal.core.typeinference.PHPTypeInferenceUtils;
import org.eclipse.php.internal.core.typeinference.context.IModelCacheContext;
import org.eclipse.php.internal.core.typeinference.evaluators.MethodCallTypeEvaluator;

@SuppressWarnings("restriction")
public class ProspectiveFactoryMethodCallTypeEvaluator extends MethodCallTypeEvaluator {

	private final static int STATE_INIT = 0;
	private final static int STATE_WAITING_TYPE = 1;
	private final static int STATE_CLASSFACTORYDETECTED = 2;
	private final static int STATE_NOTFACTORYMETHOD = 99;

	private int state = STATE_INIT;
	private List<FactoryMethod> prospectiveFactoryMethods;
	private String[] methodArguments;
	private IEvaluatedType result;

	public ProspectiveFactoryMethodCallTypeEvaluator(ExpressionTypeGoal goal,
			List<FactoryMethod> prospectiveFactoryMethods, String[] methodArguments) {
		super(goal);
		this.prospectiveFactoryMethods = prospectiveFactoryMethods;
		this.methodArguments = methodArguments;
	}

	@Override
	public IGoal[] init() {
		IGoal goal = this.produceNextSubgoal(null, null, null);
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	@Override
	public IGoal[] subGoalDone(IGoal subgoal, Object result, GoalState state) {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.subGoalDone(subgoal, result, state);
		}
		IGoal goal = this.produceNextSubgoal(subgoal, (IEvaluatedType) result, state);
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.init();
		}
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	@Override
	public Object produceResult() {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return super.produceResult();
		}
		return this.result;
	}

	private IGoal produceNextSubgoal(IGoal previousGoal, IEvaluatedType previousResult, GoalState goalState) {
		if (this.state == STATE_NOTFACTORYMETHOD) {
			return null;
		}
		if (this.state == STATE_INIT) {
			ExpressionTypeGoal goal = (ExpressionTypeGoal) this.goal;
			PHPCallExpression expression = (PHPCallExpression) goal.getExpression();
			ASTNode receiver = expression == null ? null : expression.getReceiver();
			if (receiver == null) {
				this.state = STATE_NOTFACTORYMETHOD;
			} else {
				this.state = STATE_WAITING_TYPE;
				return new ExpressionTypeGoal(goal.getContext(), receiver);
			}
		} else if (this.state == STATE_WAITING_TYPE) {
			FactoryMethod factoryMethod = null;
			if (previousResult instanceof PHPClassType) {
				PHPClassType receiverClass = (PHPClassType) previousResult;
				previousResult = null;
				factoryMethod = this.checkFactoryClass(receiverClass, goal);
			}
			if (factoryMethod == null) {
				this.state = STATE_NOTFACTORYMETHOD;
			} else {
				this.state = STATE_CLASSFACTORYDETECTED;
				if (factoryMethod.discrimintatorIndex < this.methodArguments.length) {
					String methodArgument = this.methodArguments[factoryMethod.discrimintatorIndex];
					if (methodArgument != null) {
						String resultingClass = null;
						if (factoryMethod.aliases.containsKey(methodArgument)) {
							resultingClass = factoryMethod.aliases.get(methodArgument);
						} else if (factoryMethod.generateClasses) {
							resultingClass = methodArgument;
						} else {
							resultingClass = null;
						}
						if (resultingClass != null && goalState != GoalState.PRUNED) {
							this.result = new PHPClassType("\\".concat(resultingClass)); //$NON-NLS-1$
						}
					}
				}
			}
		} else {
			this.state = STATE_NOTFACTORYMETHOD;
			previousResult = null;
		}
		return null;
	}

	private FactoryMethod getFactoryMethodByClass(String className) {
		if (className == null || className.isEmpty()) {
			return null;
		}
		for (FactoryMethod fm : this.prospectiveFactoryMethods) {
			if (fm.className.equalsIgnoreCase(className)) {
				return fm;
			}
		}
		return null;
	}

	private FactoryMethod getFactoryMethodByClass(PHPClassType classType) {
		String className = (classType == null) ? null : classType.getTypeName();
		if (className == null || className.length() < 2
				|| className.charAt(0) != NamespaceReference.NAMESPACE_SEPARATOR) {
			return null;
		}
		return this.getFactoryMethodByClass(className.substring(1));
	}

	private FactoryMethod getFactoryMethodByClass(IType classType) {
		String className = (classType == null) ? null
				: classType.getTypeQualifiedName(NamespaceReference.NAMESPACE_DELIMITER);
		if (className == null || className.isEmpty()) {
			return null;
		}
		return this.getFactoryMethodByClass(className);
	}

	private FactoryMethod checkFactoryClass(PHPClassType classType, IGoal goal) {
		if (classType == null) {
			return null;
		}
		FactoryMethod result;
		result = this.getFactoryMethodByClass(classType);
		if (result != null) {
			return null;
		}
		IContext context = goal.getContext();
		if (context instanceof ISourceModuleContext) {
			IModelAccessCache cache = (context instanceof IModelCacheContext)
					? ((IModelCacheContext) context).getCache()
					: null;
			IType[] types = PHPTypeInferenceUtils.getModelElements(classType, (ISourceModuleContext) context, 0, cache);
			if (types != null) {
				for (IType type : types) {
					ITypeHierarchy hierarchy = null;
					if (cache != null) {
						try {
							hierarchy = cache.getSuperTypeHierarchy(type, null);
						} catch (ModelException e) {
						}
					}
					IType[] superClasses;
					try {
						superClasses = PHPModelUtils.getSuperClasses(type, hierarchy);
					} catch (ModelException e) {
						superClasses = null;
					}
					if (superClasses != null) {
						for (IType superClass : superClasses) {
							result = this.getFactoryMethodByClass(superClass);
							if (result != null) {
								return result;
							}
						}
					}
				}

			}
		}
		return null;
	}
}
