// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run;

import com.intellij.execution.lineMarker.RunLineMarkerContributorBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.templateLanguages.OuterLanguageElementImpl;
import com.intellij.sh.ShBundle;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

final class ShRunLineMarkerContributor implements RunLineMarkerContributorBase, DumbAware {
  @Override
  public @Nullable InfoBase getInfo(@NotNull PsiElement element) {
    if (element instanceof OuterLanguageElementImpl || !(element instanceof LeafElement leaf) || element.getTextRange().getStartOffset() != 0)
      return null;
    // A remote-development client shows the marker the host computed; a local one would wrap an action that never runs there.
    if (IdeProductMode.isFrontend() && !IdeProductMode.isLight()) return null;
    var contributionProhibited = ContainerUtil.exists(ShRunnerAdditionalCondition.EP.getExtensionsIfPointIsRegistered(), additionalCondition -> {
      return additionalCondition.isRunningProhibitedForElement(element);
    });
    if (contributionProhibited) return null;
    PsiFile psiFile = element.getContainingFile();
    if (!(psiFile instanceof ShFile) && !StringUtil.startsWith(leaf.getChars(), "#!")) return null;

    AnAction action = ActionManager.getInstance().getAction(ShRunFileAction.ID);
    if (action == null) return null;
    return new InfoBase() {
      @Override
      public @NotNull Icon getIcon() {
        return AllIcons.RunConfigurations.TestState.Run;
      }

      @Override
      public @NotNull List<@NotNull AnAction> getActions() {
        return List.of(action);
      }

      @Override
      public Function1<@NotNull PsiElement, @Nullable String> getTooltipProvider() {
        return psiElement -> ShBundle.message("line.marker.run.0", psiElement.getContainingFile().getName());
      }
    };
  }
}
