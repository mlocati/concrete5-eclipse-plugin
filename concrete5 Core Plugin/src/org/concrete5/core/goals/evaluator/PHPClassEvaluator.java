package org.concrete5.core.goals.evaluator;

import org.eclipse.dltk.ti.GoalState;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.php.internal.core.typeinference.PHPClassType;

@SuppressWarnings("restriction")
public class PHPClassEvaluator extends GoalEvaluator {

	private String phpClassName;

	public PHPClassEvaluator(IGoal goal, String phpClassName) {
		super(goal);
		this.phpClassName = phpClassName;
	}

	public Object produceResult() {
		return new PHPClassType(this.phpClassName);
	}

	public IGoal[] init() {
		return null;
	}

	public IGoal[] subGoalDone(IGoal subgoal, Object result, GoalState state) {
		return null;
	}
}
