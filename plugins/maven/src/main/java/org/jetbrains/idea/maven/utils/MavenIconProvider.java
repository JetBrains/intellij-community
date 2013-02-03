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
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;

/**
 * @author peter
 */
public class MavenIconProvider implements DumbAware, FileIconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
    if(project == null) return null;

    if (MavenProjectsManager.getInstance(project).findProject(file) != null) {
      return MavenIcons.MavenLogo;
    }

    return null;
  }
}
