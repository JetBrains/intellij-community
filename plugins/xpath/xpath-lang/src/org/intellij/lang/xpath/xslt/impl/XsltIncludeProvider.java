// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.lang.xpath.xslt.impl;

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
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class XsltIncludeProvider extends FileIncludeProvider {
  @Override
  @NotNull
  public String getId() {
    return "xslt";
  }

  @Override
  public boolean acceptFile(VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
  }

  @Override
  @NotNull
  public FileIncludeInfo[] getIncludeInfos(FileContent content) {
    CharSequence contentAsText = content.getContentAsText();
    if (CharArrayUtil.indexOf(contentAsText, XsltSupport.XSLT_NS, 0) == -1) return FileIncludeInfo.EMPTY;
    final ArrayList<FileIncludeInfo> infos = new ArrayList<>();
    NanoXmlBuilder builder = new NanoXmlBuilder() {
      boolean isXslt;
      boolean isInclude;
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
        boolean isXsltTag = XsltSupport.XSLT_NS.equals(nsURI);
        if (!isXslt) { // analyzing start tag
          if (!isXsltTag) {
            throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
          } else {
            isXslt = true;
          }
        }
        isInclude = isXsltTag && ("include".equals(name) || "import".equals(name));
      }

      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
        if (isInclude && "href".equals(key)) {
          infos.add(new FileIncludeInfo(value));
        }
      }

      @Override
      public void endElement(String name, String nsPrefix, String nsURI) {
        isInclude = false;
      }
    };

    NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(contentAsText), builder);
    return infos.toArray(FileIncludeInfo.EMPTY);
  }
}
