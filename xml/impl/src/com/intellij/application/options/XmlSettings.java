package com.intellij.application.options;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */

@State(
  name="XmlSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/editor.codeinsight.xml"
    )}
)
public class XmlSettings implements PersistentStateComponent<XmlSettings>, ExportableComponent {

  public boolean SHOW_XML_ADD_IMPORT_HINTS = true;

  public static XmlSettings getInstance() {
    return ServiceManager.getService(XmlSettings.class);
  }

  public XmlSettings getState() {
    return this;
  }

  public void loadState(final XmlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("editor.codeinsight")};
  }

  @NotNull
  public String getPresentableName() {
    return XmlBundle.message("xml.settings");
  }
}
