package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Time;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;

public class IcsSettings {
  private static final int DEFAULT_COMMIT_DELAY = Time.MINUTE * 5;
  private static final int DEFAULT_UPDATE_ON_ACTIVITY_DELAY = Time.HOUR * 2;

  private static final SkipDefaultValuesSerializationFilters DEFAULT_FILTER = new SkipDefaultValuesSerializationFilters();

  @Tag
  public boolean shareProjectWorkspace;
  @Tag
  public boolean updateOnStart = true;

  @SuppressWarnings("UnusedDeclaration")
  @Tag
  public int updateOnActivityDelay = DEFAULT_UPDATE_ON_ACTIVITY_DELAY;

  @Tag
  public int commitDelay = DEFAULT_COMMIT_DELAY;

  public boolean doNoAskMapProject;

  @Transient
  private final File settingsFile;

  public IcsSettings() {
    settingsFile = new File(IcsManager.getPluginSystemDir(), "state.xml");
  }

  public void save() {
    FileUtil.createParentDirs(settingsFile);

    try {
      Element serialized = XmlSerializer.serialize(this, DEFAULT_FILTER);
      if (!serialized.getContent().isEmpty()) {
        JDOMUtil.writeDocument(new Document(serialized), settingsFile, "\n");
      }
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
      updateOnActivityDelay = DEFAULT_UPDATE_ON_ACTIVITY_DELAY;
    }
  }
}