/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.maven.core;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenConstants;

import javax.swing.*;

/**
 * @author peter
 */
public class MavenIconProvider extends IconProvider {
  public static final Icon mavenIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof XmlFile && Comparing.strEqual(((XmlFile)element).getName(), MavenConstants.POM_XML)) {
      return mavenIcon;
    }
    return null;
  }
}
