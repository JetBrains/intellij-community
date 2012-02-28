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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineProcessEventListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.idea.svn.SvnAuthenticationManager;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.portable.SvnExceptionWrapper;
import org.jetbrains.idea.svn.portable.SvnSvnkitUpdateClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/1/12
 * Time: 12:13 PM
 */
public class SvnCommandLineUpdateClient extends SvnSvnkitUpdateClient {
  private static final Pattern ourExceptionPattern = Pattern.compile("svn: E(\\d{6}): .+");
  private static final String ourAuthenticationRealm = "Authentication realm:";
  private final Project myProject;
  private final VirtualFile myCommonAncestor;

  public SvnCommandLineUpdateClient(final Project project, VirtualFile commonAncestor) {
    super(SvnVcs.getInstance(project).createUpdateClient());
    myProject = project;
    myCommonAncestor = commonAncestor;
  }

  @Override
  public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {
    final long[] longs = doUpdate(new File[]{file}, revision, SVNDepth.fromRecurse(recursive), false, false, false);
    return longs[0];
  }

  @Override
  public long doUpdate(File file, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
    final long[] longs = doUpdate(new File[]{file}, revision, SVNDepth.fromRecurse(recursive), force, false, false);
    return longs[0];
  }

  @Override
  public long[] doUpdate(File[] paths, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SVNException {
    return doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky, false);
  }

  @Override
  public long[] doUpdate(final File[] paths, final SVNRevision revision, final SVNDepth depth, final boolean allowUnversionedObstructions,
                         final boolean depthIsSticky, final boolean makeParents) throws SVNException {
    // since one revision is passed -> I assume same repository here
    final SvnCommandLineInfoClient infoClient = new SvnCommandLineInfoClient(myProject);
    final SVNInfo info = infoClient.doInfo(paths[0], SVNRevision.UNDEFINED);
    if (info == null || info.getURL() == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, paths[0].getPath()));
    }
    final long[] result = new long[paths.length];

    new CommandLineAuthenticator(myProject, new CommandLineAuthenticator.AuthenticationRequiringCommand() {
      @Override
      public void run(File configDir) throws SVNException {
        File base = myCommonAncestor == null ? paths[0] : new File(myCommonAncestor.getPath());
        base = base.isDirectory() ? base : base.getParentFile();
        final SvnLineCommand command = new SvnLineCommand(myProject, base, SvnCommandName.up);
        if (revision != null && ! SVNRevision.UNDEFINED.equals(revision) && ! SVNRevision.WORKING.equals(revision)) {
          command.addParameters("-r", revision.toString());
        }
        // unknown depth is not used any more for 1.7 -> why?
        if (depth != null && ! SVNDepth.UNKNOWN.equals(depth)) {
          command.addParameters("--depth", depth.toString());
        }
        if (allowUnversionedObstructions) {
          command.addParameters("--force");
        }
        if (depthIsSticky && depth != null) {// !!! not sure, but not used
          command.addParameters("--set-depth", depth.toString());
        }
        if (makeParents) {
          command.addParameters("--parents");
        }
        command.addParameters("--accept", "postpone");
        command.addParameters("--config-dir", configDir.getPath());

        for (File path : paths) {
          command.addParameters(path.getPath());
        }

        final StringBuffer sbError = new StringBuffer();
        final Semaphore semaphore = new Semaphore();
        semaphore.down();
        final ISVNEventHandler handler = getEventHandler();
        final UpdateOutputLineConverter converter = new UpdateOutputLineConverter(base);
        final SVNException[] innerException = new SVNException[1];
        command.addListener(new LineProcessEventListener() {
          @Override
          public void onLineAvailable(String line, Key outputType) {
            if (ProcessOutputTypes.STDOUT.equals(outputType)) {
              final SVNEvent event = converter.convert(line);
              if (event != null) {
                checkForUpdateCompleted(event);
                try {
                  handler.handleEvent(event, 0.5);
                }
                catch (SVNException e) {
                  command.cancel();
                  semaphore.up();
                  innerException[0] = e;
                }
              }
            } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
              sbError.append(line);
              if (line.contains(ourAuthenticationRealm)) {
                command.cancel();
                semaphore.up();
              }
            }
          }

          @Override
          public void processTerminated(int exitCode) {
            semaphore.up();
          }

          @Override
          public void startFailed(Throwable exception) {
            semaphore.up();
          }
        });
        try {
          command.start();
          semaphore.waitFor();

          checkForException(sbError);
        } catch (SvnExceptionWrapper e){
          throw (SVNException) e.getCause();
        }
      }

      @Override
      public void runWithSvnkitClient(File configDir, SvnAuthenticationManager manager) throws SVNException {
        final SVNUpdateClient client = new SVNUpdateClient(manager, SvnConfiguration.getInstance(myProject).getOptions(myProject));
        client.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky, makeParents);
      }

      private void checkForUpdateCompleted(SVNEvent event) {
        if (SVNEventAction.UPDATE_COMPLETED.equals(event.getAction())) {
          final long eventRevision = event.getRevision();
          for (int i = 0; i < paths.length; i++) {
            final File path = paths[i];
            if (path.equals(event.getFile())) {
              result[i] = eventRevision;
              break;
            }
          }
        }
      }

      @Override
      public SVNURL sampleUrl() {
        return info.getURL();
      }

      @Override
      public void cleanup() throws SVNException {
        final SvnVcs vcs17 = SvnVcs.getInstance(myProject);
        final SVNWCClient client = vcs17.createWCClient();
        for (File path : paths) {
          client.doCleanup(path);
        }
      }
    }).doWithAuthentication();
    return result;
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
  public long doUpdate(File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SVNException {
    final long[] longs = doUpdate(new File[]{path}, revision, depth, allowUnversionedObstructions, depthIsSticky, false);
    return longs[0];
  }

  @Override
  public long doSwitch(File file, SVNURL url, SVNRevision revision, boolean recursive) throws SVNException {
    throw new UnsupportedOperationException();
    //return super.doSwitch(file, url, revision, recursive);
  }

  @Override
  public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
    throw new UnsupportedOperationException();
    //return super.doSwitch(file, url, pegRevision, revision, recursive);
  }

  @Override
  public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force)
    throws SVNException {
    throw new UnsupportedOperationException();
    //return super.doSwitch(file, url, pegRevision, revision, recursive, force);
  }

  @Override
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean allowUnversionedObstructions,
                       boolean depthIsSticky) throws SVNException {
    throw new UnsupportedOperationException();
    //return super.doSwitch(path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky);
  }

  @Override
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean allowUnversionedObstructions,
                       boolean depthIsSticky,
                       boolean ignoreAncestry) throws SVNException {
    throw new UnsupportedOperationException();
    // todo MAIN
    //return super.doSwitch(path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky, ignoreAncestry);
  }
}
