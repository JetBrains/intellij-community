// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class DomFileIconProvider extends IconProvider implements DumbAware {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof XmlFile) {
      DomFileDescription<?> description = DomManager.getDomManager(element.getProject()).getDomFileDescription((XmlFile)element);
      if (description == null) {
        return null;
      }
      final Icon fileIcon = description.getFileIcon(flags);
      if (fileIcon != null) {
        return IconManager.getInstance().createLayeredIcon(element, fileIcon, ElementBase.transformFlags(element, flags));
      }
    }
    return null;
  }
}
