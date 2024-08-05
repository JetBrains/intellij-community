// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.model.resolve;

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
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncInclude;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

final class RelaxIncludeProvider extends FileIncludeProvider {
  @Override
  public @NotNull String getId() {
    return "relax-ng";
  }

  @Override
  public boolean acceptFile(@NotNull VirtualFile file) {
    final FileType type = file.getFileType();
    return type == XmlFileType.INSTANCE || type == RncFileType.getInstance();
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
    fileTypeSink.consume(RncFileType.getInstance());
  }

  @Override
  public FileIncludeInfo @NotNull [] getIncludeInfos(@NotNull FileContent content) {
    final ArrayList<FileIncludeInfo> infos;

    if (content.getFileType() == XmlFileType.INSTANCE) {
      CharSequence inputDataContentAsText = content.getContentAsText();
      if (CharArrayUtil.indexOf(inputDataContentAsText, RelaxNgMetaDataContributor.RNG_NAMESPACE, 0) == -1) return FileIncludeInfo.EMPTY;
      infos = new ArrayList<>();
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(content.getContentAsText()), new RngBuilderAdapter(infos));
    }
    else if (content.getFileType() == RncFileType.getInstance()) {
      infos = new ArrayList<>();
      content.getPsiFile().acceptChildren(new RncElementVisitor() {
        @Override
        public void visitElement(RncElement element) {
          element.acceptChildren(this);
        }

        @Override
        public void visitInclude(RncInclude include) {
          final String path = include.getFileReference();
          if (path != null) {
            infos.add(new FileIncludeInfo(path));
          }
        }
      });
    } else {
      return FileIncludeInfo.EMPTY;
    }
    return infos.toArray(FileIncludeInfo.EMPTY);
  }

  private static class RngBuilderAdapter implements NanoXmlBuilder {
    boolean isRNG;
    boolean isInclude;
    private final ArrayList<? super FileIncludeInfo> myInfos;

    RngBuilderAdapter(ArrayList<? super FileIncludeInfo> infos) {
      myInfos = infos;
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      boolean isRngTag = RelaxNgMetaDataContributor.RNG_NAMESPACE.equals(nsURI);
      if (!isRNG) { // analyzing start tag
        if (isRngTag) {
          isRNG = true;
        }
        else {
          throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
        }
      }
      isInclude = isRngTag && "include".equals(name);
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
      if (isInclude && "href".equals(key)) {
        myInfos.add(new FileIncludeInfo(value));
      }
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      isInclude = false;
    }
  }
}
