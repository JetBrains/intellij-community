/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.xml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.StdXMLReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndex extends FileBasedIndexExtension<XmlPropertiesIndex.Key, String>
  implements FileBasedIndex.FileTypeSpecificInputFilter, DataIndexer<XmlPropertiesIndex.Key, String, FileContent>,
             KeyDescriptor<XmlPropertiesIndex.Key> {

  public final static Key MARKER_KEY = new Key();
  public static final ID<Key,String> NAME = ID.create("xmlProperties");

  private static final String HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD = "http://java.sun.com/dtd/properties.dtd";

  @NotNull
  @Override
  public ID<Key, String> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Key, String, FileContent> getIndexer() {
    return this;
  }

  @NotNull
  @Override
  public KeyDescriptor<Key> getKeyDescriptor() {
    return this;
  }

  @NotNull
  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return this;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @Override
  public boolean acceptInput(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    fileTypeSink.consume(XmlFileType.INSTANCE);
  }

  @NotNull
  @Override
  public Map<Key, String> map(@NotNull FileContent inputData) {
    CharSequence text = inputData.getContentAsText();
    if(CharArrayUtil.indexOf(text, HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD, 0) == -1) {
      return Collections.emptyMap();
    }
    MyIXMLBuilderAdapter builder = parse(text, false);
    if (builder == null) return Collections.emptyMap();
    HashMap<Key, String> map = builder.myMap;
    if (builder.accepted) map.put(MARKER_KEY, "");
    return map;
  }

  static boolean isPropertiesFile(XmlFile file) {
    Project project = file.getProject();
    if (DumbService.isDumb(project)) {
      if (!file.isValid()) {
        return false;
      }
      CharSequence contents = file.getViewProvider().getContents();
      return CharArrayUtil.indexOf(contents, HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD, 0) != -1 &&
          isAccepted(contents);
    }
    return !FileBasedIndex.getInstance().processValues(NAME, MARKER_KEY, file.getVirtualFile(),
                                                       new FileBasedIndex.ValueProcessor<String>() {
                                                         @Override
                                                         public boolean process(VirtualFile file, String value) {
                                                           return false;
                                                         }
                                                       }, GlobalSearchScope.allScope(project));
  }

  private static boolean isAccepted(CharSequence bytes) {
    MyIXMLBuilderAdapter builder = parse(bytes, true);
    return builder != null && builder.accepted;
  }

  @Nullable
  private static MyIXMLBuilderAdapter parse(CharSequence text, boolean stopIfAccepted) {
    StdXMLReader reader = new StdXMLReader(CharArrayUtil.readerFromCharSequence(text)) {
      @Override
      public Reader openStream(String publicID, String systemID) throws IOException {
        if (!HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD.equals(systemID)) throw new IOException();
        return new StringReader(" ");
      }
    };
    MyIXMLBuilderAdapter builder = new MyIXMLBuilderAdapter(stopIfAccepted);
    NanoXmlUtil.parse(reader, builder);
    return builder;
  }

  @Override
  public void save(@NotNull DataOutput out, Key value) throws IOException {
    out.writeBoolean(value.isMarker);
    if (value.key != null) {
      IOUtil.writeUTF(out, value.key);
    }
  }

  @Override
  public Key read(@NotNull DataInput in) throws IOException {
    boolean isMarker = in.readBoolean();
    return isMarker ? MARKER_KEY : new Key(IOUtil.readUTF(in));
  }

  @Override
  public int getHashCode(Key value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(Key val1, Key val2) {
    return val1.isMarker == val2.isMarker && Comparing.equal(val1.key, val2.key);
  }

  public static class Key {
    final boolean isMarker;
    final String key;

    public Key(String key) {
      this.key = key;
      isMarker = false;
    }

    public Key() {
      isMarker = true;
      key = null;
    }

    @Override
    public int hashCode() {
      return isMarker ? 0 : key.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key1 = (Key)o;

      if (isMarker != key1.isMarker) return false;
      if (key != null ? !key.equals(key1.key) : key1.key != null) return false;

      return true;
    }
  }

  private static class MyIXMLBuilderAdapter extends NanoXmlUtil.IXMLBuilderAdapter {

    boolean accepted;
    boolean insideEntry;
    String key;
    private final HashMap<Key, String> myMap = new HashMap<>();
    private final boolean myStopIfAccepted;

    public MyIXMLBuilderAdapter(boolean stopIfAccepted) {
      myStopIfAccepted = stopIfAccepted;
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
      throws Exception {
      if (!accepted) {
        if ("properties".equals(name)) {
          accepted = true;
        }
        else throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
      }
      else {
        insideEntry = "entry".equals(name);
      }
      if (myStopIfAccepted) throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
      throws Exception {
      if (insideEntry && "key".equals(key)) this.key = value;
    }

    @Override
    public void addPCData(Reader reader, String systemID, int lineNr) throws Exception {
      if (insideEntry && key != null) {
        String value = StreamUtil.readTextFrom(reader);
        myMap.put(new Key(key), value);
      }
    }
  }
}
