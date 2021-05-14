/*
 * Copyright 2005-2008 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class XsltSymbolIndex extends FileBasedIndexExtension<String, XsltSymbolIndex.Kind> {
  @NonNls
  public static final ID<String, Kind> NAME = ID.create("XsltSymbolIndex");

  @Override
  @NotNull
  public ID<String, Kind> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, Kind, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      @NotNull
      public Map<String, Kind> map(@NotNull FileContent inputData) {
        CharSequence inputDataContentAsText = inputData.getContentAsText();
        if (CharArrayUtil.indexOf(inputDataContentAsText, XsltSupport.XSLT_NS, 0) == -1) {
          return Collections.emptyMap();
        }
        final Map<String, Kind> map = new HashMap<>();
        NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlBuilder() {
          NanoXmlBuilder attributeHandler;
          int depth;

          @Override
          public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
            if (attributeHandler != null) {
              attributeHandler.addAttribute(key, nsPrefix, nsURI, value, type);
            }
          }

          @Override
          public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
            attributeHandler = null;
            if (depth == 1 && XsltSupport.XSLT_NS.equals(nsURI)) {
              if ("template".equals(name)) {
                attributeHandler = new MyAttributeHandler(map, Kind.TEMPLATE);
              }
              else if ("variable".equals(name)) {
                attributeHandler = new MyAttributeHandler(map, Kind.VARIABLE);
              }
              else if ("param".equals(name)) {
                attributeHandler = new MyAttributeHandler(map, Kind.PARAM);
              }
            }
            depth++;
          }

          @Override
          public void endElement(String name, String nsPrefix, String nsURI) {
            attributeHandler = null;
            depth--;
          }
        });
        return map;
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<Kind> getValueExternalizer() {
    return new EnumDataDescriptor<>(Kind.class);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return !(file.getFileSystem() instanceof JarFileSystem);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  enum Kind {
    PARAM(XsltParameter.class), VARIABLE(XsltVariable.class), TEMPLATE(XsltTemplate.class), ANYTHING(null);

    final Class<? extends XsltElement> myClazz;

    Kind(Class<? extends XsltElement> clazz) {
      myClazz = clazz;
    }

    @Nullable
    public XsltElement wrap(XmlTag tag) {
      final Class<? extends XsltElement> clazz;
      if (myClazz != null) {
        if (!StringUtil.toLowerCase(name()).equals(tag.getLocalName())) {
          return null;
        }
        clazz = myClazz;
      }
      else {
        try {
          clazz = valueOf(StringUtil.toUpperCase(tag.getLocalName())).myClazz;
        }
        catch (IllegalArgumentException e) {
          return null;
        }
      }
      return XsltElementFactory.getInstance().wrapElement(tag, clazz);
    }
  }

  private static class MyAttributeHandler implements NanoXmlBuilder {
    private final Map<String, Kind> myMap;
    private final Kind myKind;

    MyAttributeHandler(Map<String, Kind> map, Kind k) {
      myMap = map;
      myKind = k;
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
      if (key.equals("name") && (nsURI == null || nsURI.length() == 0) && value != null) {
        if (myMap.put(value, myKind) != null) {
          myMap.put(value, Kind.ANYTHING);
        }
      }
    }
  }
}
