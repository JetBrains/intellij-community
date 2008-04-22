package com.intellij.xml.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlSchemaIndex extends ScalarIndexExtension<String> {

  public static final ID<String,Void> INDEX_ID = ID.create("xmlIndex");
  private static final EnumeratorStringDescriptor KEY_DESCRIPTOR = new EnumeratorStringDescriptor();

  public ID<String, Void> getName() {
    return INDEX_ID;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<String, Void, FileContent>() {
      public Map<String, Void> map(final FileContent inputData) {
        final Collection<String> tags = XsdTagNameBuilder.computeTagNames(inputData.getFile());
        if (tags != null && !tags.isEmpty()) {
          final HashMap<String, Void> map = new HashMap<String, Void>(tags.size());
          for (String tag : tags) {
            map.put(tag, null);
          }
          return map;
        } else {
          return Collections.emptyMap();
        }
      }
    };
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.InputFilter() {
      public boolean acceptInput(final VirtualFile file) {
        @NonNls final String extension = file.getExtension();
        return extension != null && extension.equals("xsd");
      }
    };
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 0;
  }
}
