package com.intellij.tasks.jira.jql.psi;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.tasks.jira.jql.psi.impl.JqlArgumentListImpl;
import com.intellij.tasks.jira.jql.psi.impl.JqlHistoryPredicateImpl;

/**
 * @author Mikhail Golubev
 */
public abstract class JqlElementVisitor extends PsiElementVisitor {

  public abstract void visitJqlQuery(JqlQuery query);

  public abstract void visitJqlOrderBy(JqlOrderBy orderBy);

  public abstract void visitJqlOrClause(JqlOrClause clause);

  public abstract void visitJqlAndClause(JqlAndClause clause);

  public abstract void visitJqlNotClause(JqlNotClause clause);

  public abstract void visitJqlSimpleClause(JqlSimpleClause clause);

  public abstract void visitJqlWasClause(JqlWasClause clause);

  public abstract void visitJqlChangedClause(JqlChangedClause clause);

  public abstract void visitJqlIdentifier(JqlIdentifier identifier);

  public abstract void visitJqlLiteral(JqlLiteral literal);

  public abstract void visitEmptyValue(JqlEmptyValue emptyValue);

  public abstract void visitJqlFunctionCall(JqlFunctionCall call);

  public abstract void visitJqlList(JqlList list);

  public abstract void visitJqlSortKey(JqlSortKey key);

  public abstract void visitJqlArgumentList(JqlArgumentListImpl list);

  public abstract void visitJqlHistoryPredicate(JqlHistoryPredicateImpl predicate);
}
