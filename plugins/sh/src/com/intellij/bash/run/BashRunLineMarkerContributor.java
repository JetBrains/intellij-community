package com.intellij.bash.run;

import com.intellij.bash.psi.BashFile;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashRunLineMarkerContributor extends RunLineMarkerContributor implements DumbAware {
  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (!(element instanceof LeafElement) || element.getTextRange().getStartOffset() != 0) {
      return null;
    }
    if (!(element.getContainingFile() instanceof BashFile)) {
      return null;
    }
    AnAction[] actions = {ActionManager.getInstance().getActionOrStub(BashRunFileAction.ID)};
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
        psiElement -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> "Run " + psiElement.getContainingFile().getName()), "\n"));
  }
}
