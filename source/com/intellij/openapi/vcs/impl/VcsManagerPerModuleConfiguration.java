package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootManager;
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
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManagerImpl.getInstanceEx(myModule.getProject());
    if (!USE_PROJECT_VCS) {
      for(VirtualFile file: ModuleRootManager.getInstance(myModule).getContentRoots()) {
        vcsManager.setDirectoryMapping(file.getPath(), ACTIVE_VCS_NAME);
      }
      vcsManager.cleanupMappings();
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
