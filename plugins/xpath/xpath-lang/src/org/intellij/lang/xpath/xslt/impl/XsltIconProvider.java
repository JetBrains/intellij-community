/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
