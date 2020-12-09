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
package com.jetbrains.python.codeInsight;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyIfStatementNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyRunLineMarkerContributor extends RunLineMarkerContributor {
  public PyRunLineMarkerContributor() {
    if (PlatformUtils.isPyCharmEducational()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (isMainClauseOnTopLevel(element)) {
      final AnAction[] actions = ExecutorAction.getActions();
      Function<PsiElement, String> tooltipProvider =
        psiElement -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> getText(action, psiElement)), "\n");
      return new Info(AllIcons.RunConfigurations.TestState.Run, tooltipProvider, actions);
    }
    return null;
  }

  private static boolean isMainClauseOnTopLevel(@NotNull PsiElement element) {
    if (element.getNode().getElementType() == PyTokenTypes.IF_KEYWORD) {
      PyIfStatement statement = PyIfStatementNavigator.getIfStatementByIfKeyword(element);
      return statement != null &&
             ScopeUtil.getScopeOwner(element) instanceof PyFile &&
             PyUtil.isIfNameEqualsMain(statement);
    }
    else {
      return false;
    }
  }

  @Override
  public boolean producesAllPossibleConfigurations(@NotNull PsiFile file) {
    return false;
  }
}