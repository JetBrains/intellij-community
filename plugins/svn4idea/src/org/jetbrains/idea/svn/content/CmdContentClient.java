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
package org.jetbrains.idea.svn.content;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.util.ArrayList;
import java.util.List;

public class CmdContentClient extends BaseSvnClient implements ContentClient {

  private static final Logger LOG = Logger.getInstance(CmdContentClient.class);

  private static final String NO_PRISTINE_VERSION_FOR_FILE = "has no pristine version until it is committed";

  @Override
  public byte[] getContent(@NotNull Target target, @Nullable Revision revision, @Nullable Revision pegRevision)
    throws VcsException, FileTooBigRuntimeException {
    // TODO: rewrite this to provide output as Stream
    // TODO: Also implement max size constraint like in SvnKitContentClient
    // NOTE: Export could not be used to get content of scheduled for deletion file

    List<String> parameters = new ArrayList<>();
    CommandUtil.put(parameters, target.getPath(), pegRevision);
    CommandUtil.put(parameters, revision);

    CommandExecutor command = null;
    try {
      command = execute(myVcs, target, SvnCommandName.cat, parameters, null);
    }
    catch (SvnBindException e) {
      // "no pristine version" error is thrown, for instance, for locally replaced files (not committed yet)
      if (StringUtil.containsIgnoreCase(e.getMessage(), NO_PRISTINE_VERSION_FOR_FILE)) {
        LOG.debug(e);
      }
      else {
        throw e;
      }
    }

    byte[] bytes = command != null ? command.getBinaryOutput().toByteArray() : ArrayUtil.EMPTY_BYTE_ARRAY;
    ContentRevisionCache.checkContentsSize(target.getPath(), bytes.length);

    return bytes;
  }
}
