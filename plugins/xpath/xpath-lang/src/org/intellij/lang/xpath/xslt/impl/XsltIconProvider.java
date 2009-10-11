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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.TIntObjectHashMap;
import org.intellij.lang.xpath.xslt.XsltSupport;

import javax.swing.*;

/**
 * @author peter
 */
public class XsltIconProvider implements FileIconPatcher {

  private static final Key<TIntObjectHashMap<Icon>> ICON_KEY = Key.create("XSLT_ICON");

  public Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, Project project) {
    if (project == null) return baseIcon;

    final TIntObjectHashMap<Icon> icons = file.getUserData(ICON_KEY);
    if (icons != null) {
      final Icon icon = icons.get(flags);
      if (icon != null) {
        return icon;
      }
    }

    final PsiFile element = PsiManager.getInstance(project).findFile(file);
    if (element != null) {
      if (XsltSupport.isXsltFile(element)) {
        return cacheIcon(file, flags, icons, XsltSupport.createXsltIcon(baseIcon));
      }
    }
    return baseIcon;
  }

  private static Icon cacheIcon(VirtualFile file, int flags, TIntObjectHashMap<Icon> icons, Icon icon) {
    if (icons == null) {
      file.putUserData(ICON_KEY, icons = new TIntObjectHashMap<Icon>(3));
    }
    icons.put(flags, icon);
    return icon;
  }
}
