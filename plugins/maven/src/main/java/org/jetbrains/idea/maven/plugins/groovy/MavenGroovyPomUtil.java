// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class MavenGroovyPomUtil {

  public static @NotNull List<String> getGroovyMethodCalls(PsiElement psiElement) {
    LinkedList<String> methodCallInfo = new LinkedList<>();
    for (GrMethodCall current = PsiTreeUtil.getParentOfType(psiElement, GrMethodCall.class);
         current != null;
         current = PsiTreeUtil.getParentOfType(current, GrMethodCall.class)) {
      GrExpression expression = current.getInvokedExpression();
      String text = expression.getText();
      if (text != null) {
        methodCallInfo.addFirst(text);
      }
    }
    return methodCallInfo;
  }
}
