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
package com.jetbrains.python;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Descriptor to choose only .py files and (probably) folders.
 * @author Ilya.Kazakevich
 */
public final class PyFileChooserDescriptor extends FileChooserDescriptor {
  public PyFileChooserDescriptor(final boolean chooseFolders) {super(true, chooseFolders, false, false, false, false);}

  @Override
  public boolean isFileVisible(final VirtualFile file, final boolean showHiddenFiles) {
    return file.isDirectory() || file.getExtension() == null || Comparing.equal(file.getExtension(), "py");
  }
}
