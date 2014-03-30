/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnStatusConvertor;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdDiffClient extends BaseSvnClient implements DiffClient {

  @Override
  public List<Change> compare(@NotNull SvnTarget target1, @NotNull SvnTarget target2) throws VcsException {
    // TODO: Currently implemented only for "Compare with Branch" action - target1 is assumed to be file, target2 - repository url
    // Such combination (file and url) with "--summarize" option is supported only in svn 1.8.
    // For svn 1.7 "--summarize" is only supported when both targets are repository urls.
    assertDirectory(target1);
    assertUrl(target2);

    List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, target1);
    CommandUtil.put(parameters, target2);
    parameters.add("--xml");
    parameters.add("--summarize");

    CommandExecutor executor = CommandUtil.execute(myVcs, target1, SvnCommandName.diff, parameters, null);
    return parseOutput(target1, target2, executor);
  }

  private List<Change> parseOutput(@NotNull SvnTarget target1, @NotNull SvnTarget target2, @NotNull CommandExecutor executor)
    throws SvnBindException {
    try {
      DiffInfo diffInfo = CommandUtil.parse(executor.getOutput(), DiffInfo.class);
      List<Change> result = ContainerUtil.newArrayList();

      if (diffInfo != null && diffInfo.paths != null) {
        for (DiffPath path : diffInfo.paths.diffPaths) {
          result.add(createChange(target1, target2, path));
        }
      }

      return result;
    }
    catch (JAXBException e) {
      throw new SvnBindException(e);
    }
  }

  private ContentRevision createRemoteRevision(@NotNull FilePath remotePath, @NotNull FilePath localPath, @NotNull FileStatus status) {
    // explicitly use local path for deleted items - so these items will be correctly displayed as deleted under local working copy node
    // and not as deleted under remote branch node (in ChangesBrowser)
    // NOTE, that content is still retrieved using remotePath.
    return SvnRepositoryContentRevision
      .create(myVcs, remotePath, status == FileStatus.DELETED ? localPath : null, SVNRevision.HEAD.getNumber());
  }

  private static ContentRevision createLocalRevision(@NotNull FilePath path) {
    return CurrentContentRevision.create(path);
  }

  @NotNull
  private Change createChange(@NotNull SvnTarget target1, @NotNull SvnTarget target2, @NotNull DiffPath diffPath) throws SvnBindException {
    // TODO: 1) Unify logic of creating Change instance with SvnDiffEditor and SvnChangeProviderContext
    // TODO: 2) If some directory is switched, files inside it are returned as modified in "svn diff --summarize", even if they are equal
    // TODO: to branch files by content - possibly add separate processing of all switched files
    // TODO: 3) Properties change is currently not added as part of result change like in SvnChangeProviderContext.patchWithPropertyChange

    File oldTarget = CommandUtil.resolvePath(target1.getFile(), diffPath.path);
    String relativePath = FileUtil.getRelativePath(target1.getFile(), oldTarget);

    if (relativePath == null) {
      throw new SvnBindException("Could not get relative path for " + target1.getFile() + " and " + oldTarget);
    }

    FilePath localPath = VcsUtil.getFilePath(oldTarget, diffPath.isDirectory());
    FilePath remotePath = VcsUtil
      .getFilePathOnNonLocal(SVNPathUtil.append(target2.getPathOrUrlDecodedString(), FileUtil.toSystemIndependentName(relativePath)),
                             diffPath.isDirectory());

    FileStatus status = SvnStatusConvertor
      .convertStatus(SvnStatusHandler.getStatus(diffPath.itemStatus), SvnStatusHandler.getStatus(diffPath.propertiesStatus));

    ContentRevision beforeRevision = status == FileStatus.ADDED ? null : createRemoteRevision(remotePath, localPath, status);
    ContentRevision afterRevision = status == FileStatus.DELETED ? null : createLocalRevision(localPath);

    return createChange(status, beforeRevision, afterRevision);
  }

  @NotNull
  private static Change createChange(@NotNull final FileStatus status,
                                     @Nullable final ContentRevision beforeRevision,
                                     @Nullable final ContentRevision afterRevision) {
    // isRenamed() and isMoved() are always false here not to have text like "moved from ..." in changes window - by default different
    // paths in before and after revisions are treated as move, but this is not the case for "Compare with Branch"
    return new Change(beforeRevision, afterRevision, status) {
      @Override
      public boolean isRenamed() {
        return false;
      }

      @Override
      public boolean isMoved() {
        return false;
      }
    };
  }

  @XmlRootElement(name = "diff")
  public static class DiffInfo {

    @XmlElement(name = "paths")
    public DiffPaths paths;
  }

  public static class DiffPaths {

    @XmlElement(name = "path")
    public List<DiffPath> diffPaths = new ArrayList<DiffPath>();
  }

  public static class DiffPath {

    @XmlAttribute(name = "kind")
    public String kind;

    @XmlAttribute(name = "props")
    public String propertiesStatus;

    @XmlAttribute(name = "item")
    public String itemStatus;

    @XmlValue
    public String path;

    public boolean isDirectory() {
      return SVNNodeKind.DIR.equals(SVNNodeKind.parseKind(kind));
    }
  }
}
