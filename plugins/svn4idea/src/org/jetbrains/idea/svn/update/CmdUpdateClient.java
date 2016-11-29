/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/1/12
 * Time: 12:13 PM
 */
// TODO: Currently make inherit SVNKit update implementation not to duplicate setXxx() methods.
public class CmdUpdateClient extends SvnKitUpdateClient {
  private static final Pattern ourExceptionPattern = Pattern.compile("svn: E(\\d{6}): .+");
  private static final String ourAuthenticationRealm = "Authentication realm:";

  private void checkWorkingCopy(@NotNull File path) throws SvnBindException {
    final Info info = myFactory.createInfoClient().doInfo(path, SVNRevision.UNDEFINED);

    if (info == null || info.getURL() == null) {
      throw new SvnBindException(SVNErrorCode.WC_NOT_WORKING_COPY, path.getPath());
    }
  }

  private long[] run(@NotNull File path, @NotNull List<String> parameters, @NotNull SvnCommandName command) throws SvnBindException {
    File base = path.isDirectory() ? path : path.getParentFile();

    final AtomicReference<long[]> updatedToRevision = new AtomicReference<>();
    updatedToRevision.set(new long[0]);

    final BaseUpdateCommandListener listener = createCommandListener(new File[]{path}, updatedToRevision, base);
    execute(myVcs, SvnTarget.fromFile(base), command, parameters, listener);

    listener.throwWrappedIfException();

    return updatedToRevision.get();
  }

  private BaseUpdateCommandListener createCommandListener(final File[] paths,
                                                          final AtomicReference<long[]> updatedToRevision,
                                                          final File base) {
    return new BaseUpdateCommandListener(base, myDispatcher) {
      final long[] myRevisions = new long[paths.length];

      @Override
      protected void beforeHandler(@NotNull ProgressEvent event) {
        if (EventAction.UPDATE_COMPLETED.equals(event.getAction())) {
          final long eventRevision = event.getRevision();
          for (int i = 0; i < paths.length; i++) {
            final File path = paths[i];
            if (FileUtil.filesEqual(path, event.getFile())) {
              myRevisions[i] = eventRevision;
              break;
            }
          }
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        super.processTerminated(exitCode);
        updatedToRevision.set(myRevisions);
      }
    };
  }

  private static void fillParameters(@NotNull List<String> parameters,
                                     @Nullable SVNRevision revision,
                                     @Nullable Depth depth,
                                     boolean depthIsSticky,
                                     boolean allowUnversionedObstructions) {

    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth, depthIsSticky);
    CommandUtil.put(parameters, allowUnversionedObstructions, "--force");
    parameters.add("--accept");
    parameters.add("postpone");
  }


  private void checkForException(final StringBuffer sbError) throws SVNException {
    if (sbError.length() == 0) return;
    final String message = sbError.toString();
    final Matcher matcher = ourExceptionPattern.matcher(message);
    if (matcher.matches()) {
      final String group = matcher.group(1);
      if (group != null) {
        try {
          final int code = Integer.parseInt(group);
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.getErrorCode(code), message));
        } catch (NumberFormatException e) {
          //
        }
      }
    }
    if (message.contains(ourAuthenticationRealm)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, message));
    }
    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, message));
 }

  @Override
  public long doUpdate(File path, SVNRevision revision, Depth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SvnBindException {
    checkWorkingCopy(path);

    final List<String> parameters = new ArrayList<>();

    fillParameters(parameters, revision, depth, depthIsSticky, allowUnversionedObstructions);
    CommandUtil.put(parameters, myIgnoreExternals, "--ignore-externals");
    CommandUtil.put(parameters, path);

    final long[] longs = run(path, parameters, SvnCommandName.up);
    return longs[0];
  }

  @Override
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       Depth depth,
                       boolean allowUnversionedObstructions,
                       boolean depthIsSticky) throws SvnBindException {
    checkWorkingCopy(path);

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, SvnTarget.fromURL(url, pegRevision));
    CommandUtil.put(parameters, path, false);
    fillParameters(parameters, revision, depth, depthIsSticky, allowUnversionedObstructions);
    if (!myVcs.is16SupportedByCommandLine() ||
        WorkingCopyFormat.from(myFactory.createVersionClient().getVersion()).isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN)) {
      parameters.add("--ignore-ancestry");
    }

    long[] revisions = run(path, parameters, SvnCommandName.switchCopy);

    return revisions != null && revisions.length > 0 ? revisions[0] : -1;
  }
}
