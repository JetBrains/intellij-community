package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * @author mike
 */
public class VcsManagerPerModuleConfiguration implements JDOMExternalizable, ModuleComponent {
  public String ACTIVE_VCS_NAME = "";
  public boolean USE_PROJECT_VCS = true;

  public static VcsManagerPerModuleConfiguration getInstance(Module module) {
    return module.getComponent(VcsManagerPerModuleConfiguration.class);
  }

  public void moduleAdded() {

  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getComponentName() {
    //return "VcsManagerPerModuleConfiguration";
    return "VcsManagerConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if ("".equals(ACTIVE_VCS_NAME) && USE_PROJECT_VCS) {
      throw new WriteExternalException();
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
