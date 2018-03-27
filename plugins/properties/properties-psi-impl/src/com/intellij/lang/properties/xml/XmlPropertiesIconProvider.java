/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.xml;

import com.intellij.ide.IconProvider;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import icons.PropertiesIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlPropertiesIconProvider extends IconProvider {

  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    return element instanceof XmlFile &&
           ((XmlFile)element).getFileType() == XmlFileType.INSTANCE &&
           PropertiesImplUtil.getPropertiesFile((XmlFile)element) != null ? PropertiesIcons.XmlProperties : null;
  }
}
