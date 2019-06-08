// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class XIncludeProvider extends FileIncludeProvider {
  @NotNull
  @Override
  public String getId() {
    return "XInclude";
  }

  @Override
  public boolean acceptFile(VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
  }

  @NotNull
  @Override
  public FileIncludeInfo[] getIncludeInfos(FileContent content) {
    CharSequence contentAsText = content.getContentAsText();
    if (CharArrayUtil.indexOf(contentAsText, XmlUtil.XINCLUDE_URI, 0) == -1) return FileIncludeInfo.EMPTY;
    final ArrayList<FileIncludeInfo> infos = new ArrayList<>();
    NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(contentAsText), new NanoXmlBuilder() {
      boolean isXInclude;
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
        isXInclude = XmlUtil.XINCLUDE_URI.equals(nsURI) && "include".equals(name);
      }

      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
        if (isXInclude && "href".equals(key)) {
          infos.add(new FileIncludeInfo(value));
        }
      }

      @Override
      public void endElement(String name, String nsPrefix, String nsURI) {
        isXInclude = false;
      }
    });
    return infos.toArray(FileIncludeInfo.EMPTY);
  }
}
