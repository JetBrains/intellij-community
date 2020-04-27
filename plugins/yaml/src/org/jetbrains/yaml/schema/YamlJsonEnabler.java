// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;

public class YamlJsonEnabler implements JsonSchemaEnabler {
  @Override
  public boolean isEnabledForFile(@NotNull VirtualFile file, @Nullable Project project) {
    FileType type = file.getFileType();
    if (type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof YAMLLanguage) return true;
    if (project == null || !ScratchUtil.isScratch(file)) return false;
    RootType rootType = ScratchFileService.findRootType(file);
    return rootType != null && rootType.substituteLanguage(project, file) instanceof YAMLLanguage;
  }

  @Override
  public boolean canBeSchemaFile(VirtualFile file) {
    return isEnabledForFile(file, null /*avoid checking scratches*/);
  }
}
