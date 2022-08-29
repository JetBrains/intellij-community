// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;

public final class YAMLFileBasedIndexUtil {

  public static final FileBasedIndex.InputFilter YAML_INPUT_FILTER =
    new DefaultFileTypeSpecificInputFilter(YAMLFileType.YML);

  /**
   * Differs from {@link #YAML_INPUT_FILTER} in that this filter matches all files that support YAML, even if they are not pure YAML files.
   * For example Ruby on Rails' fixture files, which are YAML files that could contain ERb tags.
   */
  public static final FileBasedIndex.ProjectSpecificInputFilter CONTAINING_YAML_INPUT_FILTER = indexedFile -> {
    final Project project = indexedFile.getProject();
    final VirtualFile vFile = indexedFile.getFile();
    final PsiFile pFile = PsiManager.getInstance(project).findFile(vFile);
    if (pFile == null) {
      return false;
    }
    return pFile.getViewProvider().hasLanguage(YAMLLanguage.INSTANCE);
  };
}
