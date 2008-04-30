package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
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

  protected static VirtualFileFilter createFilter(final Project project) {

    return new VirtualFileFilter() {

      private final ProjectFileIndex myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      private final VirtualFile myStandardSchemas = ExternalResourcesRootsProvider.getStandardSchemas();

      public boolean accept(final VirtualFile file) {
        return myFileIndex.isInContent(file) || myFileIndex.isInLibraryClasses(file) || file.getParent() == myStandardSchemas;
      }
    };
  }

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
