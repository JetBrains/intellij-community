package com.intellij.tasks.jira.jql.psi;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.tasks.jira.jql.psi.impl.JqlArgumentListImpl;
import com.intellij.tasks.jira.jql.psi.impl.JqlHistoryPredicateImpl;
import com.intellij.tasks.jira.jql.psi.impl.JqlSubClauseImpl;

/**
 * @author Mikhail Golubev
 */
public abstract class JqlElementVisitor extends PsiElementVisitor {

  public void visitJqlQuery(JqlQuery query) {
    visitElement(query);
  }

  public void visitJqlOrderBy(JqlOrderBy orderBy){
    visitElement(orderBy);
  }

  public void visitJqlOrClause(JqlOrClause clause) {
    visitElement(clause);
  }

  public void visitJqlAndClause(JqlAndClause clause) {
    visitElement(clause);
  }

  public void visitJqlNotClause(JqlNotClause clause) {
    visitElement(clause);
  }

  public void visitJqlSimpleClause(JqlSimpleClause clause) {
    visitElement(clause);
  }

  public void visitJqlWasClause(JqlWasClause clause) {
    visitJqlSimpleClause(clause);
  }

  public void visitJqlChangedClause(JqlChangedClause clause) {
    visitElement(clause);
  }

  public void visitJqlIdentifier(JqlIdentifier identifier) {
    visitElement(identifier);
  }

  public void visitJqlLiteral(JqlLiteral literal) {
    visitElement(literal);
  }

  public void visitEmptyValue(JqlEmptyValue emptyValue) {
    visitElement(emptyValue);
  }

  public void visitJqlFunctionCall(JqlFunctionCall call) {
    visitElement(call);
  }

  public void visitJqlList(JqlList list) {
    visitElement(list);
  }

  public void visitJqlSortKey(JqlSortKey key) {
    visitElement(key);
  }

  public void visitJqlArgumentList(JqlArgumentListImpl list) {
    visitElement(list);
  }

  public void visitJqlHistoryPredicate(JqlHistoryPredicateImpl predicate) {
    visitElement(predicate);
  }

  public void visitJqlSubClause(JqlSubClauseImpl subClause) {
    visitElement(subClause);
  }
}
