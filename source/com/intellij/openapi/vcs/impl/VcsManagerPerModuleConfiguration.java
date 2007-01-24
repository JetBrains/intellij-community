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
  private Module myModule;
  public String ACTIVE_VCS_NAME = "";
  public boolean USE_PROJECT_VCS = true;

  public static VcsManagerPerModuleConfiguration getInstance(Module module) {
    return module.getComponent(VcsManagerPerModuleConfiguration.class);
  }

  public VcsManagerPerModuleConfiguration(final Module module) {
    myModule = module;
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
    return "VcsManagerConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (!USE_PROJECT_VCS) {
      ProjectLevelVcsManagerImpl.getInstanceEx(myModule.getProject()).addMappingFromModule(myModule, ACTIVE_VCS_NAME);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!USE_PROJECT_VCS) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    } else {
      throw new WriteExternalException();
    }
  }
}
