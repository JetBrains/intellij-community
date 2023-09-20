// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.index;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlIndex<V> extends FileBasedIndexExtension<String, V> {
  protected static GlobalSearchScope createFilter(@NotNull Project project) {
    GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);
    return new GlobalSearchScope(project) {
      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return projectScope.compare(file1, file2);
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        final VirtualFile parent = file.getParent();
        return parent != null && (parent.getName().equals("standardSchemas") || projectScope.contains(file));
      }

      @Override
      public boolean isSearchInLibraries() {
        return true;
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(final @NotNull VirtualFile file) {
        if (!"xsd".equals(file.getExtension())) {
          return false;
        }
        if (file.isInLocalFileSystem()) {
          return true;
        }
        VirtualFile parent = file.getParent();
        return parent != null && parent.getName().equals("standardSchemas");
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }
}
