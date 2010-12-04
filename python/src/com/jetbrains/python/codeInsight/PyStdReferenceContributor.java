package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PyStdReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class).inside(
                                          psiElement(PyAssignmentStatement.class).withFirstChild(
                                            psiElement(PyTargetExpression.class).withName(PyNames.ALL))),
                                        new PyDunderAllReferenceProvider());
  }
}
