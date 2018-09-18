package com.intellij.xml.arrangement;

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsPanel;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlArrangementPanel extends ArrangementSettingsPanel {
  private final FileType myFileType;

  public XmlArrangementPanel(@NotNull CodeStyleSettings settings, XMLLanguage language, FileType fileType) {
    super(settings, language);
    myFileType = fileType;
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return myFileType;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }
}
