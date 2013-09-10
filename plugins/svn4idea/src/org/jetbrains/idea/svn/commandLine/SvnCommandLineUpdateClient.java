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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.jetbrains.idea.svn.portable.SvnSvnkitUpdateClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

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
public class SvnCommandLineUpdateClient extends SvnSvnkitUpdateClient {
  private static final Pattern ourExceptionPattern = Pattern.compile("svn: E(\\d{6}): .+");
  private static final String ourAuthenticationRealm = "Authentication realm:";
  private final Project myProject;
  private final VirtualFile myCommonAncestor;
  private boolean myIgnoreExternals;

  public SvnCommandLineUpdateClient(final SvnVcs vcs, VirtualFile commonAncestor) {
    super(vcs.createUpdateClient());
    myProject = vcs.getProject();
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
    final AtomicReference<long[]> updatedToRevision = new AtomicReference<long[]>();
    updatedToRevision.set(new long[0]);

    File base = myCommonAncestor == null ? paths[0] : new File(myCommonAncestor.getPath());
    base = base.isDirectory() ? base : base.getParentFile();

    final List<String> parameters = prepareParameters(paths, revision, depth, allowUnversionedObstructions, depthIsSticky, makeParents);
    final BaseUpdateCommandListener listener = createCommandListener(paths, updatedToRevision, base);
    try {
      SvnLineCommand.runWithAuthenticationAttempt(SvnApplicationSettings.getInstance().getCommandLinePath(),
                                                  base, info.getURL(), SvnCommandName.up, listener,
                                                  new IdeaSvnkitBasedAuthenticationCallback(SvnVcs.getInstance(myProject)),
                                                  ArrayUtil.toStringArray(parameters));
    }
    catch (SvnBindException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
    }

    listener.throwIfException();

    return updatedToRevision.get();
  }

  private BaseUpdateCommandListener createCommandListener(final File[] paths,
                                                          final AtomicReference<long[]> updatedToRevision,
                                                          final File base) {
    return new BaseUpdateCommandListener(base, getEventHandler()) {
      final long[] myRevisions = new long[paths.length];

      @Override
      protected void beforeHandler(@NotNull SVNEvent event) {
        if (SVNEventAction.UPDATE_COMPLETED.equals(event.getAction())) {
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

  private List<String> prepareParameters(File[] paths,
                                         SVNRevision revision,
                                         SVNDepth depth,
                                         boolean allowUnversionedObstructions,
                                         boolean depthIsSticky, boolean makeParents) {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, allowUnversionedObstructions, "--force");
    if (depthIsSticky && depth != null) {// !!! not sure, but not used
      parameters.add("--set-depth");
      parameters.add(depth.toString());
    }
    CommandUtil.put(parameters, makeParents, "--parents");
    CommandUtil.put(parameters, myIgnoreExternals, "--ignore-externals");
    parameters.add("--accept");
    parameters.add("postpone");
    CommandUtil.put(parameters, paths);

    return parameters;
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

  @Override
  public void setIgnoreExternals(boolean ignoreExternals) {
    myIgnoreExternals = ignoreExternals;
  }
}
