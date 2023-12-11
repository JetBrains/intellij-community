// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyIfStatementNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyRunLineMarkerContributor extends RunLineMarkerContributor implements DumbAware {
  public PyRunLineMarkerContributor() {
    if (PlatformUtils.isPyCharmEducational()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (isMainClauseOnTopLevel(element)) {
      AnAction[] actions = ExecutorAction.getActions();
      AnActionEvent event = createActionEvent(element);
      Function<PsiElement, String> tooltipProvider = o -> StringUtil.join(ContainerUtil.mapNotNull(
        actions, action -> getText(action, event)), "\n");
      return new Info(AllIcons.RunConfigurations.TestState.Run, tooltipProvider, actions);
    }
    return null;
  }

  private static boolean isMainClauseOnTopLevel(@NotNull PsiElement element) {
    if (element.getNode().getElementType() == PyTokenTypes.IF_KEYWORD) {
      PyIfStatement statement = PyIfStatementNavigator.getIfStatementByIfKeyword(element);
      return statement != null &&
             ScopeUtil.getScopeOwner(element) instanceof PyFile containingFile &&
             containingFile.getVirtualFile().getFileType() == PythonFileType.INSTANCE &&
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