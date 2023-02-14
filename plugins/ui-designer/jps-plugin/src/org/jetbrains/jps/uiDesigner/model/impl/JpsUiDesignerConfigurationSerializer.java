// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

public final class JpsUiDesignerConfigurationSerializer extends JpsProjectExtensionSerializer {
  JpsUiDesignerConfigurationSerializer() {
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
