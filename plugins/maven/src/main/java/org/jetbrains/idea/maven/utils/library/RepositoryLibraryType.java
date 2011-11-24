/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenIcons;

import javax.swing.*;

/**
 * @author nik
 */
public class RepositoryLibraryType extends LibraryType<RepositoryLibraryProperties> {
  private static final LibraryKind<RepositoryLibraryProperties> LIBRARY_KIND = LibraryKind.create("repository");

  public static RepositoryLibraryType getInstance() {
    return EP_NAME.findExtension(RepositoryLibraryType.class);
  }

  public RepositoryLibraryType() {
    super(LIBRARY_KIND);
  }

  @Override
  public String getCreateActionName() {
    return "From Maven...";
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                  @Nullable VirtualFile contextDirectory,
                                                  @NotNull Project project) {
    return RepositoryAttachHandler.chooseLibraryAndDownload(project, null);
  }

  @NotNull
  @Override
  public RepositoryLibraryProperties createDefaultProperties() {
    return new RepositoryLibraryProperties();
  }

  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<RepositoryLibraryProperties> component) {
    return new RepositoryLibraryEditor(component, this);
  }

  @Override
  public String getDescription(@NotNull RepositoryLibraryProperties properties) {
    final String mavenIdKey = properties.getMavenId();
    return "Library " + (mavenIdKey != null ? mavenIdKey + " " : "") + "from Maven repository";
  }

  @Override
  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }
}
