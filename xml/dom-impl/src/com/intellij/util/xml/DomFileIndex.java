// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.ide.highlighter.XmlFileType;
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
public final class DomFileIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("DomFileIndex");

  @Override
  @NotNull
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      @NotNull
      public Map<String, Void> map(@NotNull FileContent inputData) {
        Set<String> namespaces = new HashSet<>();
        XmlFileHeader header = NanoXmlUtil.parseHeader(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        ContainerUtil.addIfNotNull(namespaces, header.getPublicId());
        ContainerUtil.addIfNotNull(namespaces, header.getSystemId());
        ContainerUtil.addIfNotNull(namespaces, header.getRootTagNamespace());
        String tagName = header.getRootTagLocalName();
        if (StringUtil.isNotEmpty(tagName)) {
          Map<String, Void> result = new HashMap<>();
          DomApplicationComponent component = DomApplicationComponent.getInstance();
          for (DomFileDescription<?> description : component.getFileDescriptions(tagName)) {
            String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getRootElementClass().getName(), null);
            }
          }
          for (DomFileDescription<?> description : component.getAcceptingOtherRootTagNameDescriptions()) {
            String[] strings = description.getAllPossibleRootTagNamespaces();
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

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE);
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
