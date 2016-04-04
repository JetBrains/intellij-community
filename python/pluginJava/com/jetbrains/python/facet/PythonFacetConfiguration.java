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
package com.jetbrains.python.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;

/**
 * @author yole
 */
public class PythonFacetConfiguration extends PythonFacetSettings implements FacetConfiguration {
  private static final String SDK_NAME = "sdkName";

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
      new PythonSdkEditorTab(editorContext)
    };
  }

  public void readExternal(Element element) throws InvalidDataException {
    String sdkName = element.getAttributeValue(SDK_NAME);
    mySdk = StringUtil.isEmpty(sdkName) ? null : ProjectJdkTable.getInstance().findJdk(sdkName, PythonSdkType.getInstance().getName());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(SDK_NAME, mySdk == null ? "" : mySdk.getName());
  }

}
