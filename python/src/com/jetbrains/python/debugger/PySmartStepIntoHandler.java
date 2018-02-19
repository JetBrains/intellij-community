/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PySmartStepIntoHandler extends XSmartStepIntoHandler<PySmartStepIntoHandler.PySmartStepIntoVariant> {
  private final XDebugSession mySession;
  private final PyDebugProcess myProcess;

  public PySmartStepIntoHandler(final PyDebugProcess process) {
    mySession = process.getSession();
    myProcess = process;
  }

  @Override
  @NotNull
  public List<PySmartStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
    final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    final List<PySmartStepIntoVariant> variants = Lists.newArrayList();
    final Set<PyCallExpression> visitedCalls = Sets.newHashSet();

    final int line = position.getLine();
    XDebuggerUtil.getInstance().iterateLine(mySession.getProject(), document, line, psiElement -> {
      addVariants(document, line, psiElement, variants, visitedCalls);
      return true;
    });

    return variants;
  }

  @Override
  public void startStepInto(@NotNull PySmartStepIntoVariant smartStepIntoVariant) {
    myProcess.startSmartStepInto(smartStepIntoVariant.getFunctionName());
  }

  @Override
  public String getPopupTitle(@NotNull XSourcePosition position) {
    return PyBundle.message("debug.popup.title.step.into.function");
  }

  private static void addVariants(Document document, int line, @Nullable PsiElement element,
                                  List<PySmartStepIntoVariant> variants,
                                  Set<PyCallExpression> visited) {
    if (element == null) return;

    final PyCallExpression expression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (expression != null &&
        expression.getTextRange().getEndOffset() <= document.getLineEndOffset(line) &&
        visited.add(expression)) {
      addVariants(document, line, expression.getParent(), variants, visited);
      PyExpression ref = expression.getCallee();

      variants.add(new PySmartStepIntoVariant(ref));
    }
  }

  public static class PySmartStepIntoVariant extends XSmartStepIntoVariant {
    //private final String myFunctionName;

    private final PyElement myElement;

    public PySmartStepIntoVariant(PyElement element) {
      myElement = element;
    }

    @Override
    public String getText() {
      return myElement.getText() + "()";
    }

    public String getFunctionName() {
      String name = myElement.getName();
      return name != null ? name : getText();
    }
  }
}
