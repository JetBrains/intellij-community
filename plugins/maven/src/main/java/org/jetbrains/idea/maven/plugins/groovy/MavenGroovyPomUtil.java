/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 9/1/2016
 */
public class MavenGroovyPomUtil {

  @NotNull
  public static List<String> getGroovyMethodCalls(PsiElement psiElement) {
    LinkedList<String> methodCallInfo = ContainerUtilRt.newLinkedList();
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
