package com.intellij.ide.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public abstract class XmlLikeFileType extends LanguageFileType {
  public XmlLikeFileType(Language language) {
    super(language);
  }
  public String getCharset(@NotNull VirtualFile file, final byte[] content) {
    String charset = XmlUtil.extractXmlEncodingFromProlog(content);
    return charset == null ? CharsetToolkit.UTF8 : charset;
  }

  public Charset extractCharsetFromFileContent(final Project project, @NotNull final VirtualFile file, @NotNull final String content) {
    String name = XmlUtil.extractXmlEncodingFromProlog(content);
    Charset charset = CharsetToolkit.forName(name);
    return charset == null ? CharsetToolkit.UTF8_CHARSET : charset;
  }
}
