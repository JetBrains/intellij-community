package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * User: anna
 * Date: Dec 7, 2004
 */
public interface InspectionProfile {

  String getName();

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

  InspectionTool getInspectionTool(String displayName);

  InspectionTool[] getInspectionTools(Project project);

  LocalInspectionTool[] getHighlightingLocalInspectionTools();

  File getFile();

  InspectionProfileManager getManager();

  void cleanup();

  boolean wasInitialized();

  ModifiableModel getModifiableModel();

  boolean isToolEnabled(HighlightDisplayKey key);

  interface ModifiableModel {

    InspectionProfile getParentProfile();

    String getBaseProfileName();

    void setBaseProfile(InspectionProfileImpl profile);

    void removeInheritance(boolean inheritFromBaseBase);

    String getName();

    void setName(String name);

    void enableTool(String inspectionTool);

    void disableTool(String inspectionTool);

    void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level);

    HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey);

    boolean isToolEnabled(HighlightDisplayKey key);

    void commit();

    boolean isChanged();

    void setModified(final boolean toolsSettingsChanged);

    VisibleTreeState getExpandedNodes();

    boolean isProperSetting(HighlightDisplayKey key);

    void setAdditionalJavadocTags(String tags);

    void resetToBase();

    LocalInspectionToolWrapper[] getLocalInspectionToolWrappers();

    InspectionTool[] getInspectionTools(Project project);

    String getAdditionalJavadocTags();

    void copyFrom(InspectionProfileImpl profile);

    void inheritFrom(InspectionProfileImpl profile);

    void loadAdditionalSettingsFromBaseProfile();
  }
}
