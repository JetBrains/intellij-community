/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.util.FilePathByPathComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.FilePathUtil;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.util.*;

public class SvnUpdateContext implements SequentialUpdatesContext {
  private final Set<File> myUpdatedExternals;
  private final SvnVcs myVcs;
  private final List<FilePath> myContentRoots;

  public SvnUpdateContext(final SvnVcs vcs, FilePath[] contentRoots) {
    myVcs = vcs;
    myContentRoots = Arrays.asList(contentRoots);
    Collections.sort(myContentRoots, FilePathByPathComparator.getInstance());
    myUpdatedExternals = new HashSet<>();
  }

  @NotNull
  public String getMessageWhenInterruptedBeforeStart() {
    // never
    return null;
  }

  public boolean shouldFail() {
    return false;
  }

  public void registerExternalRootBeingUpdated(final File root) {
    myUpdatedExternals.add(root);
  }

  public boolean shouldRunFor(final File ioRoot) {
    boolean result = true;

    if (myUpdatedExternals.contains(ioRoot)) {
      result = false;
    }
    else if (FilePathUtil.isNested(myContentRoots, ioRoot)) {
      final RootUrlInfo info = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(ioRoot);

      if (info != null) {
        if (NestedCopyType.switched.equals(info.getType())) {
          result = false;
        }
        else if (NestedCopyType.external.equals(info.getType())) {
          result = !myVcs.getSvnConfiguration().isIgnoreExternals();
        }
      }
    }

    return result;
  }
}
