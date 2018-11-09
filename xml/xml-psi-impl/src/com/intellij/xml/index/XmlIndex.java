/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  protected static GlobalSearchScope createFilter(final Project project) {
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);
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
  @NotNull
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull final VirtualFile file) {
        return XmlFileType.INSTANCE.equals(file.getFileType()) && "xsd".equals(file.getExtension());
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
