/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.xml.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class XmlIconProvider extends IconProvider {
  private static final Icon ourXsdIcon = IconLoader.getIcon("/fileTypes/xsdFile.png");
  private static final Icon ourWsdlIcon = IconLoader.getIcon("/fileTypes/wsdlFile.png");
  @NonNls private static final String XSD_FILE_EXTENSION = "xsd";
  @NonNls private static final String WSDL_FILE_EXTENSION = "wsdl";

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof XmlFile) {
      final String extension = ((XmlFile)element).getVirtualFile().getExtension();
      if(XSD_FILE_EXTENSION.equals(extension)) return ourXsdIcon;
      if(WSDL_FILE_EXTENSION.equals(extension)) return ourWsdlIcon;
    }
    return null;
  }

}
