/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author Dmitry Batkovich
 */
public class CustomResourceBundlePropertiesFileNode extends PsiFileNode {
  public CustomResourceBundlePropertiesFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    setUpdateCount(-1);
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(getValue());
    assert propertiesFile != null;
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    data.setLocationString(PropertiesBundle.message("project.view.resource.bundle.tree.node.text", resourceBundle.getBaseName()));
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof CustomResourceBundlePropertiesFileNode)) {
      return false;
    }
    return Comparing.equal(getValue(), ((CustomResourceBundlePropertiesFileNode)object).getValue());
  }
}
