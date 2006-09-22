package com.intellij.ide.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;

public abstract class XmlLikeFileType extends LanguageFileType {
  public XmlLikeFileType(Language language) {
    super(language);
  }
}
