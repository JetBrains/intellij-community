/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class XmlIconProvider extends IconProvider implements DumbAware {
  private static final Icon ourXsdIcon = IconLoader.getIcon("/fileTypes/xsdFile.png");
  private static final Icon ourWsdlIcon = IconLoader.getIcon("/fileTypes/wsdlFile.png");
  @NonNls private static final String XSD_FILE_EXTENSION = "xsd";
  @NonNls private static final String WSDL_FILE_EXTENSION = "wsdl";

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof XmlFile) {
      final VirtualFile vf = ((XmlFile)element).getVirtualFile();
      if (vf != null) {
        final String extension = vf.getExtension();
        if (XSD_FILE_EXTENSION.equals(extension)) return ourXsdIcon;
        if (WSDL_FILE_EXTENSION.equals(extension)) return ourWsdlIcon;
      }
    }
    return null;
  }

}
