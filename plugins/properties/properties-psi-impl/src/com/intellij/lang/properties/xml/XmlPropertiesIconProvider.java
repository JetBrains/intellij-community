// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.xml;

import com.intellij.ide.IconProvider;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.properties.PropertiesIcons;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public final class XmlPropertiesIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    return element instanceof XmlFile &&
           ((XmlFile)element).getFileType() == XmlFileType.INSTANCE &&
           PropertiesImplUtil.getPropertiesFile((XmlFile)element) != null ? PropertiesIcons.XmlProperties : null;
  }
}
