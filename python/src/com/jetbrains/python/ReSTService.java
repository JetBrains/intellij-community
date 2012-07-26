package com.jetbrains.python;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * User: catherine
 */
@State(name = "ReSTService",
      storages = {
      @Storage( file = StoragePathMacros.PROJECT_FILE),
      @Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/rest.xml", scheme = StorageScheme.DIRECTORY_BASED)
      }
)
public class ReSTService implements PersistentStateComponent<ReSTService> {
  public String DOC_DIR = "";
  public boolean TXT_IS_RST = false;

  public ReSTService() {
  }

  @Override
  public ReSTService getState() {
    return this;
  }

  @Override
  public void loadState(ReSTService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setWorkdir(String workDir) {
    DOC_DIR = workDir;
  }

  public static ReSTService getInstance(Project project) {
    return ServiceManager.getService(project, ReSTService.class);
  }

  public String getWorkdir() {
    return DOC_DIR;
  }

  public boolean txtIsRst() {
    return TXT_IS_RST;
  }

  public void setTxtIsRst(boolean isRst) {
    TXT_IS_RST = isRst;
  }
}
