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
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Target;

import java.io.OutputStream;
import java.util.List;

public interface DiffClient extends SvnClient {

  /**
   * @param target1 Should always be url.
   * @param target2 Could be either url or file. And should be directory if file.
   */
  @NotNull
  List<Change> compare(@NotNull Target target1, @NotNull Target target2) throws VcsException;

  void unifiedDiff(@NotNull Target target1, @NotNull Target target2, @NotNull OutputStream output) throws VcsException;
}
