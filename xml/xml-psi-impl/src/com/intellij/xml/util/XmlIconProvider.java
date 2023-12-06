// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class XmlIconProvider extends IconProvider implements DumbAware {
  private static final @NonNls String XSD_FILE_EXTENSION = "xsd";
  private static final @NonNls String WSDL_FILE_EXTENSION = "wsdl";

  @Override
  public @Nullable Icon getIcon(final @NotNull PsiElement element, final int _flags) {
    if (element instanceof XmlFile) {
      final VirtualFile vf = ((XmlFile)element).getVirtualFile();
      if (vf != null) {
        final String extension = vf.getExtension();

        if (XSD_FILE_EXTENSION.equals(extension)) {
          return IconManager.getInstance()
            .createLayeredIcon(element, AllIcons.FileTypes.XsdFile, ElementBase.transformFlags(element, _flags));
        }
        if (WSDL_FILE_EXTENSION.equals(extension)) {
          return IconManager.getInstance()
            .createLayeredIcon(element, AllIcons.FileTypes.WsdlFile, ElementBase.transformFlags(element, _flags));
        }
      }
    }
    return null;
  }
}
