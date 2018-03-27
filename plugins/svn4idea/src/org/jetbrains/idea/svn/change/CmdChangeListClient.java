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
package org.jetbrains.idea.svn.change;

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
import java.util.Arrays;
import java.util.List;

public class CmdChangeListClient extends BaseSvnClient implements ChangeListClient {

  @Override
  public void add(@NotNull String changeList, @NotNull File path, @Nullable String[] changeListsToOperate) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add(changeList);
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, Depth.EMPTY);
    if (changeListsToOperate != null) {
      CommandUtil.putChangeLists(parameters, Arrays.asList(changeListsToOperate));
    }

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, Target.on(path), SvnCommandName.changelist, parameters, null);
  }

  @Override
  public void remove(@NotNull File path) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add("--remove");
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, Depth.EMPTY);

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, Target.on(path), SvnCommandName.changelist, parameters, null);
  }
}
