/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleFacadeImpl extends CodeStyleFacade {
  private final Project myProject;

  public CodeStyleFacadeImpl() {
    this(null);
  }

  public CodeStyleFacadeImpl(final Project project) {
    myProject = project;
  }

  public int getIndentSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getIndentSize(fileType);
  }

  @Nullable
  public String getLineIndent(@NotNull final Editor editor) {
    if (myProject == null) return null;
    return CodeStyleManager.getInstance(myProject).getLineIndent(editor);
  }

  public String getLineSeparator() {
    return CodeStyleSettingsManager.getSettings(myProject).getLineSeparator();
  }

  public boolean projectUsesOwnSettings() {
    return myProject != null && CodeStyleSettingsManager.getInstance(myProject).USE_PER_PROJECT_SETTINGS;
  }

  public int getRightMargin() {
    return CodeStyleSettingsManager.getSettings(myProject).RIGHT_MARGIN;
  }

  public int getTabSize(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).getTabSize(fileType);
  }

  public boolean isSmartTabs(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).isSmartTabs(fileType);
  }

  public boolean useTabCharacter(final FileType fileType) {
    return CodeStyleSettingsManager.getSettings(myProject).useTabCharacter(fileType);
  }
}