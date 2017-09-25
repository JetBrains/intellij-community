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
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnExceptionWrapper;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusClient;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class SvnKitStatusClient extends BaseSvnClient implements StatusClient {

  private static final NotNullFactory<Map<String, File>> NAME_TO_FILE_MAP_FACTORY = () -> ContainerUtil.newHashMap();

  private SVNStatusClient myStatusClient;
  @Nullable private final MultiMap<FilePath, FilePath> myScope;
  @Nullable private final ProgressTracker myHandler;

  public SvnKitStatusClient() {
    this(null, null);
  }

  public SvnKitStatusClient(@Nullable MultiMap<FilePath, FilePath> scope, @Nullable ProgressTracker handler) {
    myScope = scope;
    myHandler = handler;
  }

  @Override
  public long doStatus(@NotNull File path,
                       @Nullable SVNRevision revision,
                       @NotNull Depth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       @NotNull final StatusConsumer handler) throws SvnBindException {
    try {
      return getStatusClient().doStatus(path, revision, toDepth(depth), remote, reportAll, includeIgnored, collectParentExternals,
                                        status -> {
                                          try {
                                            handler.consume(Status.create(status));
                                          }
                                          catch (SvnBindException e) {
                                            throw new SvnExceptionWrapper(e);
                                          }
                                        }, null);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
    catch (SvnExceptionWrapper e) {
      Throwable cause = e.getCause();
      if (cause instanceof SvnBindException) {
        throw ((SvnBindException)cause);
      }
      throw new SvnBindException(cause);
    }
  }

  @Override
  @Nullable
  public Status doStatus(@NotNull File path, boolean remote) throws SvnBindException {
    try {
      return Status.create(getStatusClient().doStatus(path, remote));
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private SVNStatusClient getStatusClient() {
    // if either provider or handler is specified - we reuse same status client for all further doStatus() calls
    return myHandler != null || myScope != null ? ensureStatusClient() : myVcs.getSvnKitManager().createStatusClient();
  }

  @NotNull
  private SVNStatusClient ensureStatusClient() {
    if (myStatusClient == null) {
      myStatusClient = myVcs.getSvnKitManager().createStatusClient();
      myStatusClient.setFilesProvider(myScope != null ? createFileProvider(myScope) : null);
      myStatusClient.setEventHandler(toEventHandler(myHandler));
    }

    return myStatusClient;
  }

  @NotNull
  private static ISVNStatusFileProvider createFileProvider(@NotNull MultiMap<FilePath, FilePath> nonRecursiveScope) {
    final Map<String, Map<String, File>> result = ContainerUtil.newHashMap();

    for (Map.Entry<FilePath, Collection<FilePath>> entry : nonRecursiveScope.entrySet()) {
      File file = entry.getKey().getIOFile();

      Map<String, File> fileMap = ContainerUtil.getOrCreate(result, file.getAbsolutePath(), NAME_TO_FILE_MAP_FACTORY);
      for (FilePath path : entry.getValue()) {
        fileMap.put(path.getName(), path.getIOFile());
      }

      // also add currently processed file to the map of its parent, as there are cases when SVNKit calls ISVNStatusFileProvider with file
      // parent (and not file that was passed to doStatus()), gets null result and does not provide any status
      // see http://issues.tmatesoft.com/issue/SVNKIT-567 for details
      if (file.getParentFile() != null) {
        Map<String, File> parentMap = ContainerUtil.getOrCreate(result, file.getParentFile().getAbsolutePath(), NAME_TO_FILE_MAP_FACTORY);

        parentMap.put(file.getName(), file);
      }
    }

    return parent -> result.get(parent.getAbsolutePath());
  }
}
