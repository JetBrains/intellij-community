package com.intellij.uiDesigner;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GuiDesignerConfiguration implements ProjectComponent, JDOMExternalizable{
  public static GuiDesignerConfiguration getInstance(final Project project){
    return project.getComponent(GuiDesignerConfiguration.class);
  }

  /**
   * Defines how the designer generate UI (instrument classes or generate Java code)
   */
  public boolean INSTRUMENT_CLASSES = true;
  
  public boolean COPY_FORMS_RUNTIME_TO_OUTPUT = true;

  public String DEFAULT_LAYOUT_MANAGER = UIFormXmlConstants.LAYOUT_INTELLIJ;

  public void projectOpened() {}

  public void projectClosed() {}

  @NotNull
  public String getComponentName() {
    return "uidesigner-configuration";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public void readExternal(final Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
