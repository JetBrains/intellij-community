package org.jetbrains.idea.maven.core;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.javaee.ExternalResourceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import javax.swing.*;

/**
 * Author: Vladislav.Kaznacheev
 */
public class MavenApplicationComponent implements ApplicationComponent, IconProvider {
  public static final Icon mavenIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  @NonNls
  @NotNull
  public String getComponentName() {
    return "MavenApplicationComponent";
  }

  public void initComponent() {
    ExternalResourceManager.getInstance().addStdResource("http://maven.apache.org/POM/4.0.0","maven-v4_0_0.xsd",getClass());
  }

  public void disposeComponent() {
  }

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof XmlFile && Comparing.strEqual(((XmlFile)element).getName(), MavenEnv.POM_FILE)) {
      return mavenIcon;
    }
    return null;
  }
}

