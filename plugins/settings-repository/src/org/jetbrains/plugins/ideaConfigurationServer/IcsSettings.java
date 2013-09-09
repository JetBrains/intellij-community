package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Time;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class IcsSettings {
  private static final int DEFAULT_COMMIT_DELAY = Time.MINUTE * 5;
  private static final int DEFAULT_UPDATE_ON_ACTIVITY_DELAY = Time.HOUR * 2;

  @Tag
  private String login;
  @Tag
  public String token;

  public boolean shareProjectWorkspace;

  public boolean updateOnStart = true;
  @SuppressWarnings("UnusedDeclaration")
  public int updateOnActivityDelay = DEFAULT_UPDATE_ON_ACTIVITY_DELAY;

  public int commitDelay = DEFAULT_COMMIT_DELAY;

  @Transient
  private final File settingsFile;

  public IcsSettings() {
    settingsFile = new File(IcsManager.PLUGIN_SYSTEM_DIR, "state.xml");
  }

  public void save() {
    FileUtil.createParentDirs(settingsFile);

    XmlSerializer.serialize(this);
    try {
      JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(this)), settingsFile, "\n");
    }
    catch (IOException e) {
      IcsManager.LOG.error(e);
    }
  }

  public void load() throws JDOMException, IOException {
    if (!settingsFile.exists()) {
      return;
    }

    XmlSerializer.deserializeInto(this, JDOMUtil.loadDocument(settingsFile).getRootElement());

    if (commitDelay < 0) {
      commitDelay = DEFAULT_COMMIT_DELAY;
    }
    if (updateOnActivityDelay < 0) {
      commitDelay = DEFAULT_UPDATE_ON_ACTIVITY_DELAY;
    }
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