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
package org.jetbrains.idea.svn.conflict;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdConflictClient extends BaseSvnClient implements ConflictClient {

  // TODO: Add possibility to resolve content, property and tree conflicts separately.
  // TODO: Or rewrite logic to have one "Resolve conflicts" action instead of separate actions for each conflict type.
  @Override
  public void resolve(@NotNull File path,
                      @Nullable Depth depth,
                      boolean resolveProperty,
                      boolean resolveContent,
                      boolean resolveTree) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, depth);
    parameters.add("--accept");
    parameters.add("working");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, Target.on(path), SvnCommandName.resolve, parameters, null);
  }
}
