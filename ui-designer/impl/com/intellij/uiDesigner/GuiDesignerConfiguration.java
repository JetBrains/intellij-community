package com.intellij.uiDesigner;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.psi.PsiModifier;

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
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/uiDesigner.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
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

  public String DEFAULT_FIELD_ACCESSIBILITY = PsiModifier.PRIVATE;


  public GuiDesignerConfiguration getState() {
    return this;
  }

  public void loadState(GuiDesignerConfiguration object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
