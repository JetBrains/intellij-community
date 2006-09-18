package com.intellij.ide.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public abstract class XmlLikeFileType extends LanguageFileType {
  public XmlLikeFileType(Language language) {
    super(language);
  }

  public String getCharset(@NotNull VirtualFile file) {
    String charset = XmlUtil.extractXmlEncodingFromProlog(file);
    return charset == null ? CharsetToolkit.UTF8 : charset;
  }
}
