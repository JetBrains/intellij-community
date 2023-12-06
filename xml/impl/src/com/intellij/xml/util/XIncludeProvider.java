// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.dom.StaxFactory;
import org.codehaus.stax2.XMLStreamReader2;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;

final class XIncludeProvider extends FileIncludeProvider {
  @Override
  public @NotNull String getId() {
    return "XInclude";
  }

  @Override
  public boolean acceptFile(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE);
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
  }

  @Override
  public FileIncludeInfo @NotNull [] getIncludeInfos(@NotNull FileContent content) {
    CharSequence contentAsText = content.getContentAsText();
    if (CharArrayUtil.indexOf(contentAsText, XmlUtil.XINCLUDE_URI, 0) == -1) {
      return FileIncludeInfo.EMPTY;
    }

    List<FileIncludeInfo> infos = new ArrayList<>();
    try {
      XMLStreamReader2 reader = StaxFactory.createXmlStreamReader(new CharSequenceReader(contentAsText));
      while (reader.hasNext()) {
        int next = reader.next();
        if (next == XMLStreamConstants.START_ELEMENT) {
          if (XmlUtil.XINCLUDE_URI.equals(reader.getNamespaceURI()) && "include".equals(reader.getLocalName())) {
            int attributeCount = reader.getAttributeCount();
            if (attributeCount > 0) {
              for (int i = 0; i < attributeCount; i++) {
                String localName = reader.getAttributeLocalName(i);
                if ("href".equals(localName)) {
                  infos.add(new FileIncludeInfo(reader.getAttributeValue(i)));
                }
              }
            }
          }
        }
      }
    }
    catch (XMLStreamException e) {
      // ignore
    }
    return infos.toArray(FileIncludeInfo.EMPTY);
  }
}
