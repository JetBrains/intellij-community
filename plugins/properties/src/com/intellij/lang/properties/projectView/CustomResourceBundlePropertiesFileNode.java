/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
  public void update(PresentationData data) {
    super.update(data);
    data.setLocationString(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", getResourceBundle().getBaseName()));
  }

  @NotNull
  @Override
  public ResourceBundle getResourceBundle() {
    return Objects.requireNonNull(PropertiesImplUtil.getPropertiesFile(getValue())).getResourceBundle();
  }

  @Nullable
  @Override
  public String getTestPresentation() {
    return super.getTestPresentation() + " (custom RB: " + getResourceBundle().getBaseName() + ")";
  }
}
