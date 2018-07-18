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
package com.jetbrains.python.library;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.DummyLibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * python library type should be used if library paths to be included in Python Path
 */
public class PythonLibraryType extends LibraryType<DummyLibraryProperties> {
  private static final PersistentLibraryKind<DummyLibraryProperties> LIBRARY_KIND = new PersistentLibraryKind<DummyLibraryProperties>("python") {
    @NotNull
    @Override
    public DummyLibraryProperties createDefaultProperties() {
      return DummyLibraryProperties.INSTANCE;
    }
  };

  protected PythonLibraryType() {
    super(LIBRARY_KIND);
  }

  public static PythonLibraryType getInstance() {
    return EP_NAME.findExtension(PythonLibraryType.class);
  }

  @Override
  public String getCreateActionName() {
    return null;
  }

  @Nullable
  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                  @Nullable VirtualFile contextDirectory,
                                                  @NotNull Project project) {
    return null;
  }

  @Nullable
  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<DummyLibraryProperties> editorComponent) {
    return null;
  }

  @Nullable
  @Override
  public Icon getIcon(DummyLibraryProperties properties) {
    return null;
  }

}
