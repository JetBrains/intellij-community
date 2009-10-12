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
package com.intellij.lang.properties.editor;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.properties.PropertiesBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class ResourceBundleFileType extends FakeFileType {
  @NotNull
  public String getName() {
    return "ResourceBundle";
  }

  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("resourcebundle.fake.file.type.description");
  }

  public boolean isMyFileType(VirtualFile file) {
    return file instanceof ResourceBundleAsVirtualFile;
  }

  public SyntaxHighlighter getHighlighter(Project project, final VirtualFile virtualFile) {
    return null;
  }
}
