package com.intellij.packageDependencies;

import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.ComplementPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;

public class DependencyRule {
  private NamedScope myFromScope;
  private NamedScope myToScope;
  private boolean myDenyRule = true;

  public DependencyRule(NamedScope fromPackageSet, NamedScope toPackageSet, boolean isDenyRule) {
    myFromScope = fromPackageSet;
    myToScope = toPackageSet;
    myDenyRule = isDenyRule;
  }

  public boolean isForbiddenToUse(PsiFile from, PsiFile to) {
    if (myFromScope == null || myToScope == null) return false;
    DependencyValidationManager holder = DependencyValidationManager.getInstance(from.getProject());
    return (myDenyRule
            ? myFromScope.getValue().contains(from, holder)
            : new ComplementPackageSet(myFromScope.getValue()).contains(from, holder)) &&
           myToScope.getValue().contains(to, holder);
  }

  public String getDisplayText() {
    StringBuffer buf = new StringBuffer();
    buf.append(myDenyRule ? "Deny " : "Allow ");
    buf.append("usages of '");
    if (myToScope != null) {
      buf.append(myToScope.getName());
    }
    buf.append("' " + (myDenyRule ? " " : "only ") + "in '");
    if (myFromScope != null) {
      buf.append(myFromScope.getName());
    }
    buf.append('\'');
    return buf.toString();
  }

  public boolean equals(Object o) {
    if (!(o instanceof DependencyRule)) return false;
    DependencyRule rule = (DependencyRule)o;
    return getDisplayText().equals(rule.getDisplayText());
  }

  public int hashCode() {
    return getDisplayText().hashCode();
  }

  public DependencyRule createCopy() {
    return new DependencyRule(myFromScope == null ? null : myFromScope.createCopy(),
                              myToScope == null ? null : myToScope.createCopy(),
                              myDenyRule);
  }

  public boolean isDenyRule() {
    return myDenyRule;
  }

  public NamedScope getFromScope() {
    return myFromScope;
  }

  public void setFromScope(NamedScope fromScope) {
    myFromScope = fromScope;
  }

  public NamedScope getToScope() {
    return myToScope;
  }

  public void setToScope(NamedScope toScope) {
    myToScope = toScope;
  }
}