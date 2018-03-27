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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.diff.DiffOptions;

import java.io.File;

public interface MergeClient extends SvnClient {

  void merge(@NotNull Target source,
             @NotNull File destination,
             boolean dryRun,
             boolean reintegrate,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@NotNull Target source,
             @NotNull RevisionRange range,
             @NotNull File destination,
             @Nullable Depth depth,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@NotNull Target source1,
             @NotNull Target source2,
             @NotNull File destination,
             @Nullable Depth depth,
             boolean useAncestry,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;
}
