package org.concrete5.core.goals.evaluator;

import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ti.GoalState;
import org.eclipse.dltk.ti.goals.ExpressionTypeGoal;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.dltk.ti.types.IEvaluatedType;
import org.eclipse.php.core.compiler.ast.nodes.PHPCallExpression;
import org.eclipse.php.internal.core.typeinference.PHPClassType;
import org.eclipse.php.internal.core.typeinference.evaluators.MethodCallTypeEvaluator;

@SuppressWarnings("restriction")
public class ProspectiveFactoryMethodCallTypeEvaluator extends GoalEvaluator {
	/**
	 * Factory method flags: not a factory method.
	 */
	public static final int FACTORYMETHOD_NONE = 0x0000;

	/**
	 * Factory method flags: factory method that may return a class instance.
	 */
	public static final int FACTORYMETHOD_CLASSES = 0x0001;

	/**
	 * Factory method flags: factory method that may resolve classes.
	 */
	public static final int FACTORYMETHOD_ALIASES = 0x0002;

	private final static int STATE_INIT = 0;
	private final static int STATE_WAITING_RECEIVER = 1;
	private final static int STATE_GOT_RECEIVER = 2;
	private final static int STATE_COMPLETED = 3;
	private final static int STATE_FALLBACK = 99;

	private int state = STATE_INIT;
	private MethodCallTypeEvaluator fallback;
	private PHPClassType receiverClass;
	private int flags;
	private String methodArgument;
	private IEvaluatedType result;

	public ProspectiveFactoryMethodCallTypeEvaluator(ExpressionTypeGoal goal, int flags, String methodArgument) {
		super(goal);
		this.fallback = new MethodCallTypeEvaluator(goal);
		this.flags = flags;
		this.methodArgument = methodArgument;
	}

	@Override
	public IGoal[] init() {
		if (this.state == STATE_FALLBACK) return this.fallback.init();
		IGoal goal = this.produceNextSubgoal(null, null, null);
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	@Override
	public Object produceResult() {
		if (this.state == STATE_FALLBACK) return this.fallback.produceResult();
		return this.result;
	}

	@Override
	public IGoal[] subGoalDone(IGoal subgoal, Object result, GoalState state) {
		if (this.state == STATE_FALLBACK) return this.fallback.subGoalDone(subgoal, result, state);
		IGoal goal = this.produceNextSubgoal(subgoal, (IEvaluatedType) result, state);
		if (goal != null) {
			return new IGoal[] { goal };
		}
		return IGoal.NO_GOALS;
	}

	private IGoal produceNextSubgoal(IGoal previousGoal, IEvaluatedType previousResult, GoalState goalState) {

		ExpressionTypeGoal typedGoal = (ExpressionTypeGoal) goal;
		PHPCallExpression expression = (PHPCallExpression) typedGoal.getExpression();

		// just starting to evaluate method, evaluate method receiver first:
		if (this.state == STATE_INIT) {
			ASTNode receiver = expression.getReceiver();
			if (receiver == null) {
				this.state = STATE_GOT_RECEIVER;
			} else {
				this.state = STATE_WAITING_RECEIVER;
				return new ExpressionTypeGoal(goal.getContext(), receiver);
			}
		}

		// receiver must been evaluated now, lets evaluate method return type:
		if (this.state == STATE_WAITING_RECEIVER) {
			if (previousResult instanceof PHPClassType) {
				this.receiverClass = (PHPClassType) previousResult;
			}
			this.state = STATE_GOT_RECEIVER;
		}

		if (this.state == STATE_GOT_RECEIVER) {
			this.state = STATE_COMPLETED;
			if (this.receiverClass != null) {
				this.receiverClass.
				// @todo: check receiver type
				if (goalState != GoalState.PRUNED) {
					// @todo: check method
				}
			}
		}
		return null;
	}
}
