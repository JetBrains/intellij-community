package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import org.jdom.Element;

import java.io.File;

public class DaemonCodeAnalyzerSettings implements NamedJDOMExternalizable, Cloneable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings");

  public DaemonCodeAnalyzerSettings() {
  }

  public static DaemonCodeAnalyzerSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(DaemonCodeAnalyzerSettings.class);
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return "Error highlighting settings";
  }

  public int AUTOREPARSE_DELAY = 300;
  public boolean SHOW_ADD_IMPORT_HINTS = true;
  public String NO_AUTO_IMPORT_PATTERN = "[a-z].?";

  public boolean SHOW_METHOD_SEPARATORS = false;

  private InspectionProfileImpl myInspectionProfile;

  public InspectionProfileImpl getInspectionProfile() {
    if (myInspectionProfile == null) {
      myInspectionProfile = InspectionProfileManager.getInstance().getProfile("Default");
      myInspectionProfile.resetToBase();
    }
    if (!myInspectionProfile.wasInitialized()) {
      myInspectionProfile.load(true);
    }
    return myInspectionProfile;
  }

  //set in error settings to hightlight only
  public void setInspectionProfile(InspectionProfileImpl inspectionProfile) {
    myInspectionProfile = inspectionProfile;
  }

  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    try {
      Element rootNew = new Element("root");
      writeExternal(rootNew);

      Element rootOld = new Element("root");
      oldSettings.writeExternal(rootOld);

      if (JDOMUtil.areElementsEqual(rootOld, rootNew)) {
        oldSettings.myInspectionProfile.writeExternal(rootOld);
        myInspectionProfile.writeExternal(rootNew);
        return !JDOMUtil.areElementsEqual(rootOld, rootNew);
      }
      else {
        return true;
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }

    return false;
  }

  public String getComponentName() {
    return "DaemonCodeAnalyzerSettings";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return "editor.codeinsight";
  }

  public Object clone() {
    DaemonCodeAnalyzerSettings settings = new DaemonCodeAnalyzerSettings();
    final InspectionProfileImpl inspectionProfile = new InspectionProfileImpl("copy", InspectionProfileManager.getInstance());
    inspectionProfile.copyFrom(getInspectionProfile());
    settings.myInspectionProfile = inspectionProfile;
    settings.AUTOREPARSE_DELAY = AUTOREPARSE_DELAY;
    settings.SHOW_ADD_IMPORT_HINTS = SHOW_ADD_IMPORT_HINTS;
    settings.SHOW_METHOD_SEPARATORS = SHOW_METHOD_SEPARATORS;
    settings.NO_AUTO_IMPORT_PATTERN = NO_AUTO_IMPORT_PATTERN;
    return settings;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    InspectionProfileConvertor.getInstance().storeEditorHighlightingProfile(element);
    final String profileName = element.getAttributeValue("profile");
    if (profileName != null) {
      myInspectionProfile = InspectionProfileManager.getInstance().getProfile(profileName);
      if (profileName.equals("Default")) {
        myInspectionProfile.resetToBase();
      }
    }
    else {
      myInspectionProfile =
      InspectionProfileManager.getInstance().getProfile(InspectionProfileConvertor.OLD_HIGHTLIGHTING_SETTINGS_PROFILE);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    //clone
    if (myInspectionProfile != null) {
      element.setAttribute("profile", myInspectionProfile.getName());
    }
    else {
      element.setAttribute("profile", "Default");
    }
  }

  public boolean isImportHintEnabled() {
    return SHOW_ADD_IMPORT_HINTS;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    SHOW_ADD_IMPORT_HINTS = isImportHintEnabled;
  }
}
