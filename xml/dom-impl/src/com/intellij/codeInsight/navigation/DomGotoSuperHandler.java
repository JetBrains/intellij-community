// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.stream.Stream;

public class DomGotoSuperHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull Project project,
                     @NotNull Editor editor,
                     @NotNull PsiFile file) {

    var currentElement = DomUtil.getDomElement(editor, file);
    if (currentElement == null) {
      return;
    }

    Stream.iterate(currentElement, Objects::nonNull, DomElement::getParent)
      .flatMap(element -> {
        return DomGotoActions.DOM_GOTO_SUPER.getExtensionList()
          .stream()
          .filter(p -> p.canNavigate(element))
          .<Runnable>map(p -> () -> p.navigate(element, true));
      })
      .findFirst()
      .ifPresent(Runnable::run);
  }
}
