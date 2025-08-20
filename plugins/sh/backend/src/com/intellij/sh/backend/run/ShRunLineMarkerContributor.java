// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.run;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.templateLanguages.OuterLanguageElementImpl;
import com.intellij.sh.ShBundle;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.run.ShRunnerAdditionalCondition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ShRunLineMarkerContributor extends RunLineMarkerContributor implements DumbAware {
  @Override
  public @Nullable Info getInfo(@NotNull PsiElement element) {
    if (element instanceof OuterLanguageElementImpl || !(element instanceof LeafElement ) || element.getTextRange().getStartOffset() != 0)
      return null;
    var contributionProhibited = ContainerUtil.exists(ShRunnerAdditionalCondition.EP.getExtensionsIfPointIsRegistered(), additionalCondition -> {
      return additionalCondition.isRunningProhibitedForElement(element);
    });
    if (contributionProhibited) return null;
    PsiFile psiFile = element.getContainingFile();
    if (!(psiFile instanceof ShFile) && !element.getText().startsWith("#!")) return null;

    AnAction[] actions = {ActionManager.getInstance().getAction(ShRunFileAction.ID)};
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
        psiElement -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> ShBundle
          .message("line.marker.run.0", psiElement.getContainingFile().getName())), "\n"));
  }
}
