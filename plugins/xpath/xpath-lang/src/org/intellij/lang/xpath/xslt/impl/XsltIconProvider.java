/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import gnu.trove.TIntObjectHashMap;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class XsltIconProvider extends IconProvider {
  private boolean active;

  private static final Key<TIntObjectHashMap<Icon>> ICON_KEY = Key.create("MY_CUSTOM_ICON");
  private final XsltConfig myConfig;

  public XsltIconProvider(XsltConfig config) {
    myConfig = config;
  }

  @Nullable
  public synchronized Icon getIcon(@NotNull PsiElement element, int flags) {
      if (active || !myConfig.isEnabled()) return null;

      active = true;
      try {
          TIntObjectHashMap<Icon> icons = element.getUserData(ICON_KEY);
          if (icons != null) {
              final Icon icon = icons.get(flags);
              if (icon != null) {
                  return icon;
              }
          }
          if (element instanceof PsiFile) {
              if (XsltSupport.isXsltFile((PsiFile)element)) {
                  if (icons == null) {
                      element.putUserData(ICON_KEY, icons = new TIntObjectHashMap<Icon>(3));
                  }
                  final Icon i = XsltSupport.createXsltIcon(element.getIcon(flags));
                  icons.put(flags, i);
                  return i;
              }
          }
          return null;
      } finally {
          active = false;
      }
  }

}
