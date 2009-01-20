package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        final VirtualFile parent = file.getParent();
        assert parent != null;
        return fileIndex.isInContent(file) || fileIndex.isInLibraryClasses(file) || parent.getName().equals("standardSchemas");
      }
    };
  }


  protected static VirtualFileFilter createFilter(@NotNull final Module module) {

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    return new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        Module moduleForFile = fileIndex.getModuleForFile(file);
        if (moduleForFile != null) { // in module content
          return module.equals(moduleForFile);
        }
        if (fileIndex.isInLibraryClasses(file)) {
          List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(file);
          if (orderEntries.isEmpty()) {
            return false;
          }
          for (OrderEntry orderEntry : orderEntries) {
            Module ownerModule = orderEntry.getOwnerModule();
            if (ownerModule != null) {
              if (ownerModule.equals(module)) {
                return true;
              }
            }
          }
        }
        final VirtualFile parent = file.getParent();
        assert parent != null;
        return parent.getName().equals("standardSchemas");
      }
    };
  }

  public KeyDescriptor<String> getKeyDescriptor() {
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
