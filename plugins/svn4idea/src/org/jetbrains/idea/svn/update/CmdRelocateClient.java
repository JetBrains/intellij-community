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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdRelocateClient extends BaseSvnClient implements RelocateClient {

  @Override
  public void relocate(@NotNull File copyRoot, @NotNull SVNURL fromPrefix, @NotNull SVNURL toPrefix) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add(fromPrefix.toDecodedString());
    parameters.add(toPrefix.toDecodedString());
    CommandUtil.put(parameters, copyRoot, false);

    execute(myVcs, Target.on(copyRoot), SvnCommandName.relocate, parameters, null);
  }
}
