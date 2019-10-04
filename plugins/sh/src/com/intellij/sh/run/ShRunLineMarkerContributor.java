// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShRunLineMarkerContributor extends RunLineMarkerContributor implements DumbAware {
  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (!(element instanceof LeafElement) || element.getTextRange().getStartOffset() != 0) return null;
    PsiFile psiFile = element.getContainingFile();
    if (!(psiFile instanceof ShFile)) return null;
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectedLanguageManager.isInjectedFragment(psiFile)) return null;

    AnAction[] actions = {ActionManager.getInstance().getActionOrStub(ShRunFileAction.ID)};
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
        psiElement -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> "Run " + psiElement.getContainingFile().getName()), "\n"));
  }
}
