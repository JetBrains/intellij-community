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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class ResourceBundleFileType extends FakeFileType {
  public static final ResourceBundleFileType INSTANCE = new ResourceBundleFileType();

  @Override
  @NotNull
  public String getName() {
    return "ResourceBundle";
  }

  @Override
  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("resourcebundle.fake.file.type.description");
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return file instanceof ResourceBundleAsVirtualFile;
  }

}
