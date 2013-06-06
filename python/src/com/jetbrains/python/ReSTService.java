package com.jetbrains.python;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
@State(name = "ReSTService",
       storages = {@Storage(file = "$MODULE_FILE$")}
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

  public static ReSTService getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, ReSTService.class);
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
