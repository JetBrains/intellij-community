// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Dmitry Avdeev
 */
public final class XmlTagNamesIndex extends XmlIndex<Void> {
  public static @NotNull Collection<VirtualFile> getFilesByTagName(@NotNull String tagName, @NotNull Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, tagName, createFilter(project));
  }

  public static Collection<String> getAllTagNames(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  static final ID<String, Void> NAME = ID.create("XmlTagNames");

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
        CharSequence text = inputData.getContentAsText();
        if (Strings.indexOf(text, XmlUtil.XML_SCHEMA_URI) == -1) {
          return Collections.emptyMap();
        }

        Map<String, Void> map = new HashMap<>();
        computeTagNames(CharArrayUtil.readerFromCharSequence(text), tag -> map.put(tag, null));
        return map;
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<Void> getValueExternalizer() {
    return VoidDataExternalizer.INSTANCE;
  }

  public static void computeTagNames(@NotNull Reader reader, @NotNull Consumer<String> consumer) {
    try (reader) {
      NanoXmlUtil.parse(reader, new NanoXmlBuilder() {
        private boolean elementStarted;

        @Override
        public void startElement(@NonNls String name, @NonNls String nsPrefix, @NonNls String nsURI, String systemID, int lineNr) {
          elementStarted = nsPrefix != null && nsURI.equals(XmlUtil.XML_SCHEMA_URI) && name.equals("element");
        }

        @Override
        public void addAttribute(@NonNls String key, String nsPrefix, String nsURI, String value, String type) {
          if (elementStarted && key.equals("name")) {
            consumer.accept(value);
            elementStarted = false;
          }
        }
      });
    }
    catch (IOException ignore) {
    }
  }
}
