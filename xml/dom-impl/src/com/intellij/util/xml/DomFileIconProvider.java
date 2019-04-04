/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class DomFileIconProvider extends IconProvider implements DumbAware {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof XmlFile) {
      DomFileDescription<?> description = DomManager.getDomManager(element.getProject()).getDomFileDescription((XmlFile)element);
      if (description == null) {
        return null;
      }
      final Icon fileIcon = description.getFileIcon(flags);
      if (fileIcon != null) {
        return ElementBase.createLayeredIcon(element, fileIcon, ElementBase.transformFlags(element, flags));
      }
    }
    return null;
  }
}
