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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.FileIconPatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.intellij.lang.xpath.xslt.XsltSupport;

import javax.swing.*;

/**
 * @author peter
 */
public class XsltIconProvider implements FileIconPatcher {

  public Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, Project project) {
    if (project == null) return baseIcon;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null && XsltSupport.isXsltFile(psiFile)) {
      return XsltSupport.createXsltIcon(baseIcon);
    }
    return baseIcon;
  }

}
