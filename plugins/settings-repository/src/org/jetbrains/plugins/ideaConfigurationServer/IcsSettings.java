package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class IcsSettings {
  @Tag
  private String login;
  @Tag
  private String url;
  @Tag
  private String token;

  @Transient
  private final File settingsFile;

  public IcsSettings() {
    settingsFile = new File(PathManager.getSystemPath(), "ideaConfigurationServer/state.xml");
  }

  public void save() {
    //noinspection ResultOfMethodCallIgnored
    settingsFile.getParentFile().mkdirs();

    XmlSerializer.serialize(this);
    try {
      JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(this)), settingsFile, "\n");
    }
    catch (IOException e) {
      //ignore
    }
  }

  public void load() throws JDOMException, IOException {
    if (!settingsFile.exists()) {
      return;
    }

    XmlSerializer.deserializeInto(this, JDOMUtil.loadDocument(settingsFile).getRootElement());
  }

  @Nullable
  public String getLogin() {
    return login;
  }

  @Nullable
  public String getToken() {
    return token;
  }

  public void update(@Nullable String login, @Nullable String token) {
    this.token = login;
    this.token = token;
  }
}