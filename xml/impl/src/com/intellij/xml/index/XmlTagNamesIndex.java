package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlTagNamesIndex extends XmlIndex<Void> {

  public static Collection<VirtualFile> getFilesByTagName(String tagName, final Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, tagName, createFilter(project));
  }

  public static Collection<String> getAllTagNames() {
    return FileBasedIndex.getInstance().getAllKeys(NAME);
  }

  static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(NAME);
  }

  private static final ID<String,Void> NAME = ID.create("XmlTagNames");

  public ID<String, Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<String, Void, FileContent>() {
      public Map<String, Void> map(final FileContent inputData) {
        final Collection<String> tags = XsdTagNameBuilder.computeTagNames(new ByteArrayInputStream(inputData.getContent()));
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

  public DataExternalizer<Void> getValueExternalizer() {
    return ScalarIndexExtension.VOID_DATA_EXTERNALIZER;
  }

}
