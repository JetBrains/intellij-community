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

import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdCheckoutClient extends BaseSvnClient implements CheckoutClient {
  @Override
  public void checkout(@NotNull Target source,
                       @NotNull File destination,
                       @Nullable Revision revision,
                       @Nullable Depth depth,
                       boolean ignoreExternals,
                       boolean force,
                       @NotNull WorkingCopyFormat format,
                       @Nullable ProgressTracker handler) throws VcsException {
    validateFormat(format, getSupportedFormats());

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination, false);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, ignoreExternals, "--ignore-externals");
    CommandUtil.put(parameters, force, "--force"); // corresponds to "allowUnversionedObstructions" in SVNKit

    run(source, destination, handler, parameters);
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    ArrayList<WorkingCopyFormat> result = new ArrayList<>();

    Version version = myFactory.createVersionClient().getVersion();
    result.add(WorkingCopyFormat.from(version));

    return result;
  }

  private void run(@NotNull Target source,
                   @NotNull File destination,
                   @Nullable ProgressTracker handler,
                   @NotNull List<String> parameters) throws VcsException {
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(destination, handler);

    execute(myVcs, source, SvnCommandName.checkout, parameters, listener);

    listener.throwWrappedIfException();
  }
}
