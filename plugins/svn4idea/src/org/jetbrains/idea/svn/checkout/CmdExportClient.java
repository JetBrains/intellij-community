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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdExportClient extends BaseSvnClient implements ExportClient {

  @Override
  public void export(@NotNull Target from,
                     @NotNull File to,
                     @Nullable Revision revision,
                     @Nullable Depth depth,
                     @Nullable String nativeLineEnd,
                     boolean force,
                     boolean ignoreExternals,
                     @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, from);
    CommandUtil.put(parameters, to);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, force, "--force");
    CommandUtil.put(parameters, ignoreExternals, "--ignore-externals");
    if (!StringUtil.isEmpty(nativeLineEnd)) {
      parameters.add("--native-eol");
      parameters.add(nativeLineEnd);
    }

    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(to, handler);

    execute(myVcs, from, to, SvnCommandName.export, parameters, listener);

    listener.throwWrappedIfException();
  }
}
