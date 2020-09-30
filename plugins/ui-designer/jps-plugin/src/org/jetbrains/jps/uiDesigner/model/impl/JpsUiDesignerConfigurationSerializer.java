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
package org.jetbrains.jps.uiDesigner.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

public class JpsUiDesignerConfigurationSerializer extends JpsProjectExtensionSerializer {
  private static final SkipDefaultValuesSerializationFilters FILTERS = new SkipDefaultValuesSerializationFilters();

  public JpsUiDesignerConfigurationSerializer() {
    super("uiDesigner.xml", "uidesigner-configuration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState state = XmlSerializer.deserialize(componentTag, JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState.class);
    JpsUiDesignerExtensionService.getInstance().setUiDesignerConfiguration(element, new JpsUiDesignerConfigurationImpl(state));
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    final JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState defaultState =
      new JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState();
    JpsUiDesignerExtensionService.getInstance().setUiDesignerConfiguration(project, new JpsUiDesignerConfigurationImpl(defaultState));
  }
}
