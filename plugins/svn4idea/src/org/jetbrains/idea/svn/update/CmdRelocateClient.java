// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdRelocateClient extends BaseSvnClient implements RelocateClient {

  @Override
  public void relocate(@NotNull File copyRoot, @NotNull Url fromPrefix, @NotNull Url toPrefix) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add(fromPrefix.toDecodedString());
    parameters.add(toPrefix.toDecodedString());
    CommandUtil.put(parameters, copyRoot, false);

    execute(myVcs, Target.on(copyRoot), SvnCommandName.relocate, parameters, null);
  }
}
