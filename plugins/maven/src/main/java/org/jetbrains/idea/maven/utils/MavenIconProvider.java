/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class MavenIconProvider extends IconProvider implements DumbAware {
  public static final Icon mavenIcon = IconLoader.getIcon("/images/mavenLogo.png");

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof XmlFile && Comparing.strEqual(((XmlFile)element).getName(), MavenConstants.POM_XML)) {
      return mavenIcon;
    }
    return null;
  }
}
