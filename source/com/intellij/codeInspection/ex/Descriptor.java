package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import javax.swing.*;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  private String myText;
  private String myGroup;
  private String myDescriptorFileName;
  private HighlightDisplayKey myKey;
  private JComponent myAdditionalConfigPanel;
  private Element myConfig;
  private InspectionToolsPanel.LevelChooser myChooser;
  private InspectionTool myTool;
  private HighlightDisplayLevel myLevel;
  private boolean myEnabled = false;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.Descriptor");


  public Descriptor(String text,
                    HighlightDisplayKey key,
                    JComponent additionalConfigPanel,
                    String descriptionFileName,
                    HighlightDisplayLevel level,
                    boolean enabled) {
    myText = text;
    myGroup = "General";
    myDescriptorFileName = descriptionFileName;
    myKey = key;
    myAdditionalConfigPanel = additionalConfigPanel;
    myConfig = null;
    myEnabled = enabled;
    myChooser = new InspectionToolsPanel.LevelChooser();
    myChooser.setLevel(level);
    myLevel = level;
  }

  public Descriptor(InspectionTool tool, HighlightDisplayLevel level, boolean enabled) {
    Element config = new Element("options");
    try {
      tool.writeExternal(config);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    myText = tool.getDisplayName();
    myGroup = tool.getGroupDisplayName() != null && tool.getGroupDisplayName().length() == 0 ? "General" : tool.getGroupDisplayName();
    myDescriptorFileName = tool.getDescriptionFileName();
    myKey = HighlightDisplayKey.find(tool.getShortName());
    if (myKey == null) {
      myKey = HighlightDisplayKey.register(tool.getShortName());
    }
    myAdditionalConfigPanel = tool.createOptionsPanel();
    myConfig = config;
    myChooser = new InspectionToolsPanel.LevelChooser();
    myChooser.setLevel(level);
    myEnabled = enabled;
    myTool = tool;
    myLevel = level;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getText() {
    return myText;
  }

  public HighlightDisplayKey getKey() {
    return myKey;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public JComponent getAdditionalConfigPanel() {
    return myAdditionalConfigPanel;
  }

  public InspectionToolsPanel.LevelChooser getChooser() {
    return myChooser;
  }

  public void setChooserLevel(HighlightDisplayLevel level) {
    getChooser().setLevel(level);
  }

  public Element getConfig() {
    return myConfig;
  }

  public InspectionTool getTool() {
    return myTool;
  }

  public String getDescriptorFileName() {
    return myDescriptorFileName;
  }

  public String getGroup() {
    return myGroup;
  }


}
