package com.intellij.sh.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(name = "ShSettings", storages = @Storage("shell_script.xml"))
public class ShSettings implements PersistentStateComponent<Element> {
  private static final String SHELLCHECK_PATH_TAG = "shellcheck_path";
  private String myShellcheckPath = "";

  private ShSettings() {
  }

  @NotNull
  public static ShSettings getInstance() {
    return ServiceManager.getService(ShSettings.class);
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    if (StringUtil.isNotEmpty(myShellcheckPath)) {
      JDOMExternalizerUtil.writeCustomField(state, SHELLCHECK_PATH_TAG, myShellcheckPath);
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    String shellcheckPath = JDOMExternalizerUtil.readCustomField(state, SHELLCHECK_PATH_TAG);
    if (StringUtil.isNotEmpty(shellcheckPath)) {
      myShellcheckPath = shellcheckPath;
    }
  }

  @NotNull
  public String getShellcheckPath() {
    return myShellcheckPath;
  }

  public void setShellcheckPath(String shellcheckPath) {
    if (StringUtil.isNotEmpty(shellcheckPath)) myShellcheckPath = shellcheckPath;
  }
}
