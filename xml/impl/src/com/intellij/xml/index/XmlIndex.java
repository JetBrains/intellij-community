/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.index;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
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
public abstract class XmlIndex<V> extends FileBasedIndexExtension<String, V> {

  protected static final EnumeratorStringDescriptor KEY_DESCRIPTOR = new EnumeratorStringDescriptor();

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      @NonNls final String extension = file.getExtension();
      return extension != null && extension.equals("xsd");
    }
  };

  protected static GlobalSearchScope createFilter(final Project project) {
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);
    return new GlobalSearchScope(project) {
      public int compare(VirtualFile file1, VirtualFile file2) {
        return projectScope.compare(file1, file2);
      }

      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean contains(VirtualFile file) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          System.out.println("XmlIndex.contains: " + file.getPath() + "; id=" + ((VirtualFileWithId) file).getId());
        }

        final VirtualFile parent = file.getParent();
        assert parent != null;
        return parent.getName().equals("standardSchemas") || projectScope.contains(file);
      }

      @Override
      public boolean isSearchInLibraries() {
        return true;
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
}
