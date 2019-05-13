/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.MavenUtil;

/**
 * @author ibessonov
 */
public class MavenPomFileChooserDescriptor extends FileChooserDescriptor {

  private final Project myProject;

  public MavenPomFileChooserDescriptor(Project project) {
    super(false, true, false, false, false, false);
    myProject = project;
  }

  @Override
  public boolean isFileSelectable(VirtualFile file) {
    if (!super.isFileSelectable(file)) return false;
    return MavenUtil.streamPomFiles(myProject, file).findAny().isPresent();
  }
}
