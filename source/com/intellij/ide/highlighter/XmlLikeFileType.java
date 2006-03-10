package com.intellij.ide.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlUtil;

public abstract class XmlLikeFileType extends LanguageFileType {
  public XmlLikeFileType(Language language) {
    super(language);
  }

  public String getCharset(VirtualFile file) {
    return XmlUtil.extractXmlEncodingFromProlog(file);
  }
}
