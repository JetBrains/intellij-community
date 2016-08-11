/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharArrayUtil;
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
    NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(contentAsText), new NanoXmlUtil.IXMLBuilderAdapter() {

      boolean isXInclude;
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
        isXInclude = XmlUtil.XINCLUDE_URI.equals(nsURI) && "include".equals(name);
      }

      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
        if (isXInclude && "href".equals(key)) {
          infos.add(new FileIncludeInfo(value));
        }
      }

      @Override
      public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
        isXInclude = false;
      }
    });
    return infos.toArray(new FileIncludeInfo[infos.size()]);
  }
}
