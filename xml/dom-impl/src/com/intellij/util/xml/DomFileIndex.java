// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.impl.DomApplicationComponent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public final class DomFileIndex extends ScalarIndexExtension<String>{
  public static final ID<String,Void> NAME = ID.create("DomFileIndex");
  private final DataIndexer<String,Void, FileContent> myDataIndexer;

  public DomFileIndex() {
    myDataIndexer = new DataIndexer<String, Void, FileContent>() {
      @Override
      @NotNull
      public Map<String, Void> map(@NotNull final FileContent inputData) {
        Set<String> namespaces = new HashSet<>();
        final XmlFileHeader header = NanoXmlUtil.parseHeader(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        ContainerUtil.addIfNotNull(namespaces, header.getPublicId());
        ContainerUtil.addIfNotNull(namespaces, header.getSystemId());
        ContainerUtil.addIfNotNull(namespaces, header.getRootTagNamespace());
        final String tagName = header.getRootTagLocalName();
        if (StringUtil.isNotEmpty(tagName)) {
          Map<String, Void> result = new HashMap<>();
          final DomApplicationComponent component = DomApplicationComponent.getInstance();
          for (final DomFileDescription description : component.getFileDescriptions(tagName)) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getRootElementClass().getName(), null);
            }
          }
          for (final DomFileDescription description : component.getAcceptingOtherRootTagNameDescriptions()) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getRootElementClass().getName(), null);
            }
          }
          return result;
        }
        return Collections.emptyMap();
      }
    };
  }

  @Override
  @NotNull
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return DomApplicationComponent.getInstance().getCumulativeVersion(false);
  }
}
