package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.*;

import java.util.Set;

public class JavaMatchPredicateProvider extends MatchPredicateProvider{
  @Override
  public void collectPredicates(MatchVariableConstraint constraint, String name, MatchOptions options, Set<MatchPredicate> predicates) {
    if (constraint.isReadAccess()) {
      MatchPredicate predicate = new ReadPredicate();

      if (constraint.isInvertReadAccess()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }

    if (constraint.isWriteAccess()) {
      MatchPredicate predicate = new WritePredicate();

      if (constraint.isInvertWriteAccess()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfExprType())) {
      MatchPredicate predicate = new ExprTypePredicate(
        constraint.getNameOfExprType(),
        name,
        constraint.isExprTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );

      if (constraint.isInvertExprType()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfFormalArgType())) {
      MatchPredicate predicate = new FormalArgTypePredicate(
        constraint.getNameOfFormalArgType(),
        name,
        constraint.isFormalArgTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );
      if (constraint.isInvertFormalType()) {
        predicate = new NotPredicate(predicate);
      }
      predicates.add(predicate);
    }

  }
}
