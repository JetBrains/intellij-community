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
package com.intellij.util.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.impl.DomApplicationComponent;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class DomFileIndex extends ScalarIndexExtension<String>{
  public static final ID<String,Void> NAME = ID.create("DomFileIndex");
  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return file.getFileType() == StdFileTypes.XML;
    }
  };
  private final DataIndexer<String,Void, FileContent> myDataIndexer;

  public DomFileIndex() {
    myDataIndexer = new DataIndexer<String, Void, FileContent>() {
      @NotNull
      public Map<String, Void> map(final FileContent inputData) {
        final Set<String> namespaces = new THashSet<String>();
        final XmlFileHeader header = NanoXmlUtil.parseHeader(new ByteArrayInputStream(inputData.getContent()));
        ContainerUtil.addIfNotNull(header.getPublicId(), namespaces);
        ContainerUtil.addIfNotNull(header.getSystemId(), namespaces);
        ContainerUtil.addIfNotNull(header.getRootTagNamespace(), namespaces);
        final String tagName = header.getRootTagLocalName();
        if (StringUtil.isNotEmpty(tagName)) {
          final THashMap<String, Void> result = new THashMap<String, Void>();
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

  public ID<String, Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    final DomApplicationComponent component = DomApplicationComponent.getInstance();
    int result = 0;
    for (DomFileDescription description : component.getAllFileDescriptions()) {
      result += description.getVersion();
      result += description.getRootTagName().hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
    }
    return result;
  }

}
