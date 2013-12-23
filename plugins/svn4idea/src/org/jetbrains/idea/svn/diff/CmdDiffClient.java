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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnContentRevision;
import org.jetbrains.idea.svn.SvnStatusConvertor;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
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
    assertFile(target1);
    assertUrl(target2);

    List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, target1);
    CommandUtil.put(parameters, target2);
    parameters.add("--xml");
    parameters.add("--summarize");

    CommandExecutor executor = CommandUtil.execute(myVcs, target1, SvnCommandName.diff, parameters, null);
    return parseOutput(executor);
  }

  private List<Change> parseOutput(@NotNull CommandExecutor executor) throws SvnBindException {
    try {
      DiffInfo diffInfo = CommandUtil.parse(executor.getOutput(), DiffInfo.class);
      SvnTarget base = SvnTarget.fromFile(executor.getCommand().getWorkingDirectory());
      List<Change> result = ContainerUtil.newArrayList();

      if (diffInfo != null && diffInfo.paths != null) {
        for (DiffPath path : diffInfo.paths.diffPaths) {
          result.add(createChange(base, path));
        }
      }

      return result;
    }
    catch (JAXBException e) {
      throw new SvnBindException(e);
    }
  }

  private ContentRevision createBeforeRevision(@NotNull SvnTarget target, @NotNull String path) {
    return SvnContentRevision.createRemote(myVcs, createFilePath(target, path), SVNRevision.HEAD);
  }

  private static ContentRevision createAfterRevision(@NotNull SvnTarget target, @NotNull String path) {
    return CurrentContentRevision.create(createFilePath(target, path));
  }

  private static FilePath createFilePath(@NotNull SvnTarget target, @NotNull String path) {
    return target.isFile() ? VcsUtil.getFilePath(CommandUtil.resolvePath(target.getFile(), path)) : VcsUtil.getFilePath(path);
  }

  @NotNull
  private Change createChange(@NotNull SvnTarget target, @NotNull DiffPath diffPath) {
    // TODO: 1) Unify logic of creating Change instance with SvnDiffEditor and SvnChangeProviderContext
    // TODO: 2) If some directory is switched, files inside it are returned as modified in "svn diff --summarize", even if they are equal
    // TODO: to branch files by content - possibly add separate processing of all switched files
    // TODO: 3) Properties status is currently not used - SvnStatusConvertor.convertStatus uses properties status only if there are
    // TODO: conflicts
    FileStatus status = SvnStatusConvertor
      .convertStatus(SvnStatusHandler.getStatus(diffPath.itemStatus), SvnStatusHandler.getStatus(diffPath.propertiesStatus));

    ContentRevision beforeRevision = status == FileStatus.ADDED ? null : createBeforeRevision(target, diffPath.path);
    ContentRevision afterRevision = status == FileStatus.DELETED ? null : createAfterRevision(target, diffPath.path);

    return new Change(beforeRevision, afterRevision, status);
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
  }
}
