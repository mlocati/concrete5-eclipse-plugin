package org.concrete5.core.goals;

import org.concrete5.core.Common;
import org.concrete5.core.goals.evaluator.FactoryMethodMethodReturnTypeEvaluator;
import org.eclipse.dltk.ti.IGoalEvaluatorFactory;
import org.eclipse.dltk.ti.goals.GoalEvaluator;
import org.eclipse.dltk.ti.goals.IGoal;
import org.eclipse.php.internal.core.typeinference.goals.FactoryMethodMethodReturnTypeGoal;

@SuppressWarnings("restriction")
public class GoalEvaluatorFactory implements IGoalEvaluatorFactory {

	@Override
	public GoalEvaluator createEvaluator(IGoal goal) {
		if (goal instanceof FactoryMethodMethodReturnTypeGoal && Common.hasConcrete5Nature(goal.getContext())) {
			return new FactoryMethodMethodReturnTypeEvaluator((FactoryMethodMethodReturnTypeGoal) goal);
		}
		return null;
	}
}
