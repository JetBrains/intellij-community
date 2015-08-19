/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class RepositoryLibraryWithDescriptionType extends RepositoryLibraryTypeBase {
  @NotNull protected RepositoryLibraryDescription description;

  public RepositoryLibraryWithDescriptionType(@NotNull RepositoryLibraryDescription description) {
    this.description = description;
  }

  public RepositoryLibraryProperties createDefaultProperties() {
    return new RepositoryLibraryProperties(description.getGroupId(), description.getArtifactId(), LatestVersionId);
  }

  @Nullable
  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<RepositoryLibraryProperties> editorComponent) {
    return new RepositoryLibraryWithDescriptionEditor(editorComponent, this);
  }


  @NotNull
  public RepositoryLibraryDescription getDescription() {
    return description;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return description.getIcon();
  }

  @Nullable
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
}
