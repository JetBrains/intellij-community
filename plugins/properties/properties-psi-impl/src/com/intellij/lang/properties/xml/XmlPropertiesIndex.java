// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.xml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.StdXMLReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
public class XmlPropertiesIndex extends FileBasedIndexExtension<XmlPropertiesIndex.Key, String>
  implements DataIndexer<XmlPropertiesIndex.Key, String, FileContent>,
             KeyDescriptor<XmlPropertiesIndex.Key> {

  public static final Key MARKER_KEY = new Key();
  public static final ID<Key,String> NAME = ID.create("xmlProperties");

  private static final String HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD = "http://java.sun.com/dtd/properties.dtd";

  @Override
  public @NotNull ID<Key, String> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<Key, String, FileContent> getIndexer() {
    return this;
  }

  @Override
  public @NotNull KeyDescriptor<Key> getKeyDescriptor() {
    return this;
  }

  @Override
  public @NotNull DataExternalizer<String> getValueExternalizer() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return file.getName().endsWith(".xml");
      }
    };
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
  public @NotNull Map<Key, String> map(@NotNull FileContent inputData) {
    CharSequence text = inputData.getContentAsText();
    if (CharArrayUtil.indexOf(text, HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD, 0) == -1) {
      return Collections.emptyMap();
    }

    MyIXMLBuilderAdapter builder = parse(text, false);
    Map<Key, String> map = builder.myMap;
    if (builder.accepted) {
      map.put(MARKER_KEY, "");
    }
    return map;
  }

  @VisibleForTesting
  public static boolean isPropertiesFile(XmlFile file) {
    Project project = file.getProject();
    if (!file.isValid()) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || DumbService.isDumb(project) || NoAccessDuringPsiEvents.isInsideEventProcessing()) {
      CharSequence contents = file.getViewProvider().getContents();
      return CharArrayUtil.indexOf(contents, HTTP_JAVA_SUN_COM_DTD_PROPERTIES_DTD, 0) != -1 &&
          isAccepted(contents);
    }
    return !FileBasedIndex.getInstance().getFileData(NAME, virtualFile, project).isEmpty();
  }

  private static boolean isAccepted(CharSequence bytes) {
    return parse(bytes, true).accepted;
  }

  private static @NotNull MyIXMLBuilderAdapter parse(CharSequence text, boolean stopIfAccepted) {
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
    return val1.isMarker == val2.isMarker && Objects.equals(val1.key, val2.key);
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
    public String toString() {
      return "Key{" +
             "isMarker=" + isMarker +
             ", key='" + key + '\'' +
             '}';
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
      if (!Objects.equals(key, key1.key)) return false;

      return true;
    }
  }

  private static final class MyIXMLBuilderAdapter implements NanoXmlBuilder {
    boolean accepted;
    boolean insideEntry;
    String key;
    private final HashMap<Key, String> myMap = new HashMap<>();
    private final boolean myStopIfAccepted;

    MyIXMLBuilderAdapter(boolean stopIfAccepted) {
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
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
      if (insideEntry && "key".equals(key)) {
        this.key = value;
      }
    }

    @Override
    public void addPCData(Reader reader, String systemID, int lineNr) throws Exception {
      if (insideEntry && key != null) {
        String value = StreamUtil.readText(reader);
        myMap.put(new Key(key), value);
      }
    }
  }
}
