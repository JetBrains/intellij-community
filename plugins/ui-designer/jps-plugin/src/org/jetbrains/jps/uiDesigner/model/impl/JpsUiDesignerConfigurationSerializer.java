package org.jetbrains.jps.uiDesigner.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

/**
 * @author nik
 */
public class JpsUiDesignerConfigurationSerializer extends JpsProjectExtensionSerializer {
  private static final SkipDefaultValuesSerializationFilters FILTERS = new SkipDefaultValuesSerializationFilters();

  public JpsUiDesignerConfigurationSerializer() {
    super("uiDesigner.xml", "uidesigner-configuration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState state = XmlSerializer.deserialize(componentTag, JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState.class);
    if (state == null) {
      state = new JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState();
    }
    JpsUiDesignerExtensionService.getInstance().setUiDesignerConfiguration(element, new JpsUiDesignerConfigurationImpl(state));
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    final JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState defaultState =
      new JpsUiDesignerConfigurationImpl.UiDesignerConfigurationState();
    JpsUiDesignerExtensionService.getInstance().setUiDesignerConfiguration(project, new JpsUiDesignerConfigurationImpl(defaultState));
  }

  @Override
  public void saveExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    JpsUiDesignerConfiguration configuration = JpsUiDesignerExtensionService.getInstance().getUiDesignerConfiguration(element);
    if (configuration != null) {
      XmlSerializer.serializeInto(((JpsUiDesignerConfigurationImpl)configuration).getState(), componentTag, FILTERS);
    }
  }
}
