package com.intellij.lang.properties.xml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.StdXMLReader;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndex extends FileBasedIndexExtension<String, String>
  implements FileBasedIndex.InputFilter, DataIndexer<String, String, FileContent> {

  private static final ID<String,String> NAME = ID.create("xmlProperties");
  private static final EnumeratorStringDescriptor ENUMERATOR_STRING_DESCRIPTOR = new EnumeratorStringDescriptor();

  @Override
  public ID<String, String> getName() {
    return NAME;
  }

  @Override
  public DataIndexer<String, String, FileContent> getIndexer() {
    return this;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return ENUMERATOR_STRING_DESCRIPTOR;
  }

  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return ENUMERATOR_STRING_DESCRIPTOR;
  }

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
    return 0;
  }

  @Override
  public boolean acceptInput(VirtualFile file) {
    return XmlFileType.INSTANCE == file.getFileType();
  }

  @NotNull
  @Override
  public Map<String, String> map(FileContent inputData) {
    final HashMap<String, String> map = new HashMap<String, String>();
    StdXMLReader reader = null;
    try {
      reader = new StdXMLReader(new ByteArrayInputStream(inputData.getContent())) {
        @Override
        public Reader openStream(String publicID, String systemID) throws IOException {
          if (!"http://java.sun.com/dtd/properties.dtd".equals(systemID)) throw new IOException();
          return super.openStream(publicID, systemID);
        }
      };
    }
    catch (IOException ignore) {
      return Collections.emptyMap();
    }
    NanoXmlUtil.parse(reader, new NanoXmlUtil.IXMLBuilderAdapter() {

      boolean accepted;
      boolean insideEntry;
      String key;

      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
        throws Exception {
        if (!accepted) {
          if ("properties".equals(name)) {
            accepted = true;
          }
          else throw new NanoXmlUtil.ParserStoppedException();
        }
        else {
          insideEntry = "entry".equals(name);
        }
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
          map.put(key, value);
        }
      }
    });
    return map;
  }
}
