// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import org.jetbrains.yaml.YAMLLanguage;

public class YamlJsonEnabler implements JsonSchemaEnabler {
  @Override
  public boolean isEnabledForFile(VirtualFile file) {
    FileType type = file.getFileType();
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof YAMLLanguage;
  }
}
