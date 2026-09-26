// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * python library type should be used if library paths to be included in Python Path
 */
public final class PythonLibraryType extends LibraryType<DummyLibraryProperties> {
  private static final PersistentLibraryKind<DummyLibraryProperties> LIBRARY_KIND = new PersistentLibraryKind<>("python") {
    @Override
    public @NotNull DummyLibraryProperties createDefaultProperties() {
      return DummyLibraryProperties.INSTANCE;
    }
  };

  private PythonLibraryType() {
    super(LIBRARY_KIND);
  }

  public static PythonLibraryType getInstance() {
    return EP_NAME.findExtension(PythonLibraryType.class);
  }

  @Override
  public String getCreateActionName() {
    return null;
  }

  @Override
  public @Nullable NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                            @Nullable VirtualFile contextDirectory,
                                                            @NotNull Project project) {
    return null;
  }

  @Override
  public @Nullable LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<DummyLibraryProperties> editorComponent) {
    return null;
  }

  @Override
  public @Nullable Icon getIcon(DummyLibraryProperties properties) {
    return null;
  }

}
