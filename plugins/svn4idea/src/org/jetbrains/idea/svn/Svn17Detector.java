/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/12/12
 * Time: 3:25 PM
 */
public class Svn17Detector {
  public static final LocalFileSystem ourLfs = LocalFileSystem.getInstance();

  public static boolean is17(@Nullable final Project project, @NotNull final File file) {
    if (project != null) {
      final VirtualFile vf = ourLfs.findFileByIoFile(file);
      if (vf != null) {
        final WorkingCopy root = SvnVcs.getInstance(project).getRootsToWorkingCopies().getWcRoot(vf);
        if (root != null) {
          return root.is17Copy();   // what abt not detected inner WC ???
        }
      }
    }

    final File rootIf17 = SvnUtil.getWcCopyRootIf17(file, null);
    return rootIf17 != null;
  }

  public static boolean is17(@Nullable final Project project, final VirtualFile file) {
    if (project != null) {
      final WorkingCopy root = SvnVcs.getInstance(project).getRootsToWorkingCopies().getWcRoot(file);
      if (root != null) {
        return root.is17Copy();   // what abt not detected inner WC ???
      }
    }

    final File rootIf17 = SvnUtil.getWcCopyRootIf17(new File(file.getPath()), null);
    return rootIf17 != null;
  }
}
