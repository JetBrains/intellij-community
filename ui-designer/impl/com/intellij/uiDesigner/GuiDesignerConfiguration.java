package com.intellij.uiDesigner;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@State(
  name = "uidesigner-configuration",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )}
)
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


  public GuiDesignerConfiguration getState() {
    return this;
  }

  public void loadState(GuiDesignerConfiguration object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
