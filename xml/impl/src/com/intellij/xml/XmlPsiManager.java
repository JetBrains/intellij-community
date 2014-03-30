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
package com.intellij.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;

/**
 * Created by fedorkorotkov.
 */
public class XmlPsiManager extends AbstractProjectComponent {

  private final PsiManagerImpl myPsiManager;

  protected XmlPsiManager(Project project, PsiManagerImpl psiManager) {
    super(project);
    myPsiManager = psiManager;
  }

  @Override
  public void initComponent() {
    super.initComponent();
    new PsiTreeChangePreprocessorBase(myPsiManager) {
      @Override
      protected boolean isInsideCodeBlock(PsiElement element) {
        if (element instanceof PsiFileSystemItem) {
          return false;
        }

        if (element == null || element.getParent() == null) return true;

        final boolean isXml = element.getLanguage() instanceof XMLLanguage;
        // any xml element isn't inside a "code block"
        // cause we display even attributes and tag values in structure view
        return !isXml;
      }
    };
  }
}
