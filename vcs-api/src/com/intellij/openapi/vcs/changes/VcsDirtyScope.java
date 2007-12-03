/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.Set;

/**
 * @author max
 */
public abstract class VcsDirtyScope {
  public abstract Collection<VirtualFile> getAffectedContentRoots();
  public abstract Project getProject();
  public abstract AbstractVcs getVcs();
  public abstract Set<FilePath> getDirtyFiles();
  public abstract Set<FilePath> getDirtyFilesNoExpand();
  public abstract Set<FilePath> getRecursivelyDirtyDirectories();
  public abstract void iterate(Processor<FilePath> iterator);
  public abstract boolean belongsTo(final FilePath path);
}
