// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Dmitry Batkovich
 */
public class CustomResourceBundlePropertiesFileNode extends PsiFileNode implements ResourceBundleAwareNode {
  public CustomResourceBundlePropertiesFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    setUpdateCount(-1);
  }

  @Override
  public void update(@NotNull PresentationData data) {
    super.update(data);
    data.setLocationString(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", getResourceBundle().getBaseName()));
  }

  @Override
  public @NotNull ResourceBundle getResourceBundle() {
    return Objects.requireNonNull(PropertiesImplUtil.getPropertiesFile(getValue())).getResourceBundle();
  }

  @Override
  public @Nullable String getTestPresentation() {
    return super.getTestPresentation() + " (custom RB: " + getResourceBundle().getBaseName() + ")";
  }
}
