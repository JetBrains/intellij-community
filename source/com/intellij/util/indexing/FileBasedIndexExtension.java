package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 26, 2007
 */
public interface FileBasedIndexExtension<K, V> {
  
  ExtensionPointName<FileBasedIndexExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");
  
  String getName();
  
  DataIndexer<K, V, FileBasedIndex.FileContent> getIndexer();
  
  PersistentEnumerator.DataDescriptor<K> getKeyDescriptor();
  
  DataExternalizer<V> getValueExternalizer();
  
  FileBasedIndex.InputFilter getInputFilter();
}
