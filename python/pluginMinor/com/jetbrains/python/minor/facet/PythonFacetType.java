/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.jetbrains.python.PythonModuleTypeBase.PYTHON_MODULE;

/**
 * @author traff
 */
public class PythonFacetType extends FacetType<PythonFacet, PythonFacetType.PythonFacetConfiguration> {

  @NonNls
  private static final String ID = "Python";

  public static PythonFacetType getInstance() {
    return findInstance(PythonFacetType.class);
  }

  public PythonFacetType() {
    super(PythonFacet.ID, ID, "Python");
  }

  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (sdks.size() > 0) {
      result.setSdk(sdks.get(0));
    }
    return result;
  }

  public PythonFacet createFacet(@NotNull Module module,
                                 String name,
                                 @NotNull PythonFacetConfiguration configuration,
                                 @Nullable Facet underlyingFacet) {
    return new PythonFacet(this, module, name, configuration, underlyingFacet);
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType.getId().equals(PYTHON_MODULE));
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  public static class PythonFacetConfiguration extends PythonFacetSettings implements FacetConfiguration {
    private static final String SDK_NAME = "sdkName";

    public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
      return new FacetEditorTab[]{};
    }

    public void readExternal(Element element) throws InvalidDataException {
      String sdkName = element.getAttributeValue(SDK_NAME);
      mySdk = StringUtil.isEmpty(sdkName) ? null : ProjectJdkTable.getInstance().findJdk(sdkName, PythonSdkType.getInstance().getName());

      if (mySdk != null) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectJdkTable.JDK_TABLE_TOPIC).jdkAdded(mySdk);
      }
    }

    public void writeExternal(Element element) throws WriteExternalException {
      element.setAttribute(SDK_NAME, mySdk == null ? "" : mySdk.getName());
    }
  }
}
