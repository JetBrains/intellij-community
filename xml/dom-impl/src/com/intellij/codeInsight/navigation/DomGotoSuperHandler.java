// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

public class DomGotoSuperHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull Project project,
                     @NotNull Editor editor,
                     @NotNull PsiFile psiFile) {

    var currentElement = DomUtil.getDomElement(editor, psiFile);
    while (currentElement != null) {
      DomElementNavigationProvider provider = getDomElementNavigationProvider(currentElement);
      if (provider != null) {
        provider.navigate(currentElement, true);
        return;
      } else {
        currentElement = currentElement.getParent();
      }
    }
  }

  private static DomElementNavigationProvider getDomElementNavigationProvider(DomElement element) {
    return ContainerUtil.find(DomGotoActions.DOM_GOTO_SUPER.getExtensionList(), p -> p.canNavigate(element));
  }
}
