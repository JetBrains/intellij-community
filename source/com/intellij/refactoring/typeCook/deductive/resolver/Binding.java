package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 13, 2005
 * Time: 3:44:56 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Binding {
  PsiType apply (PsiType type);
  Binding compose (Binding b);
}
