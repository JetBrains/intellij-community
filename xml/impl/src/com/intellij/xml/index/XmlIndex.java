package com.intellij.xml.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlIndex<V> implements FileBasedIndexExtension<String, V> {

  protected static final EnumeratorStringDescriptor KEY_DESCRIPTOR = new EnumeratorStringDescriptor();

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      @NonNls final String extension = file.getExtension();
      return extension != null && extension.equals("xsd");
    }
  };

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 0;
  }

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }
}
