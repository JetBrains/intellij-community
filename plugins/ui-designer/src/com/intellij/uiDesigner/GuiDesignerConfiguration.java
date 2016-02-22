/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.uiDesigner;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifier;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@State(name = "uidesigner-configuration", storages = @Storage("uiDesigner.xml"))
public final class GuiDesignerConfiguration implements PersistentStateComponent<GuiDesignerConfiguration> {
  public static GuiDesignerConfiguration getInstance(final Project project){
    return ServiceManager.getService(project, GuiDesignerConfiguration.class);
  }

  /**
   * Defines how the designer generate UI (instrument classes or generate Java code)
   */
  public boolean INSTRUMENT_CLASSES = true;
  
  public boolean COPY_FORMS_RUNTIME_TO_OUTPUT = true;

  public String DEFAULT_LAYOUT_MANAGER = UIFormXmlConstants.LAYOUT_INTELLIJ;

  public String DEFAULT_FIELD_ACCESSIBILITY = PsiModifier.PRIVATE;

  public boolean RESIZE_HEADERS = true;

  @Override
  public GuiDesignerConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(GuiDesignerConfiguration object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
