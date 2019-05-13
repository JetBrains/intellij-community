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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.diff.DiffOptions;

public interface AnnotateClient extends SvnClient {

  void annotate(@NotNull Target target,
                @NotNull Revision startRevision,
                @NotNull Revision endRevision,
                boolean includeMergedRevisions,
                @Nullable DiffOptions diffOptions,
                @Nullable AnnotationConsumer handler) throws VcsException;
}
