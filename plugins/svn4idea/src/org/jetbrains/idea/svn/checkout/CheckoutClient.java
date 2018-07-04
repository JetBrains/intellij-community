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
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.*;

import java.io.File;
import java.util.List;

public interface CheckoutClient extends SvnClient {

  void checkout(@NotNull Target source,
                @NotNull File destination,
                @Nullable Revision revision,
                @Nullable Depth depth,
                boolean ignoreExternals,
                boolean force,
                @NotNull WorkingCopyFormat format,
                @Nullable ProgressTracker handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
