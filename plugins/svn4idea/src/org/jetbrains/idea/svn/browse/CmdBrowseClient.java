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
package org.jetbrains.idea.svn.browse;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.lock.Lock;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdBrowseClient extends BaseSvnClient implements BrowseClient {

  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable Depth depth,
                   @Nullable DirectoryEntryConsumer handler) throws VcsException {
    assertUrl(target);

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    parameters.add("--xml");

    CommandExecutor command = execute(myVcs, target, SvnCommandName.list, parameters, null);
    Info info = myFactory.createInfoClient().doInfo(target, revision);

    try {
      parseOutput(target.getURL(), command, handler, info != null ? info.getRepositoryRootURL() : null);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public long createDirectory(@NotNull SvnTarget target, @NotNull String message, boolean makeParents) throws VcsException {
    assertUrl(target);

    List<String> parameters = ContainerUtil.newArrayList();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, makeParents, "--parents");
    parameters.add("--message");
    parameters.add(message);

    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(null);

    execute(myVcs, target, SvnCommandName.mkdir, parameters, listener);

    return listener.getCommittedRevision();
  }

  private static void parseOutput(@NotNull SVNURL url,
                                  @NotNull CommandExecutor command,
                                  @Nullable DirectoryEntryConsumer handler,
                                  @Nullable SVNURL repositoryUrl)
    throws VcsException, SVNException {
    try {
      TargetLists lists = CommandUtil.parse(command.getOutput(), TargetLists.class);

      if (handler != null && lists != null) {
        for (TargetList list : lists.lists) {
          for (Entry entry : list.entries) {
            handler.consume(entry.toDirectoryEntry(url, repositoryUrl));
          }
        }
      }
    }
    catch (JAXBException e) {
      throw new SvnBindException(e);
    }
  }


  @XmlRootElement(name = "lists")
  public static class TargetLists {

    @XmlElement(name = "list")
    public List<TargetList> lists = new ArrayList<>();
  }

  public static class TargetList {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "entry")
    public List<Entry> entries = new ArrayList<>();
  }

  public static class Entry {

    @XmlAttribute(name = "kind", required = true)
    public NodeKind kind;

    @XmlElement(name = "name")
    public String name;

    @XmlElement(name = "size")
    public long size;

    public CommitInfo.Builder commit;

    public Lock.Builder lock;

    @NotNull
    public DirectoryEntry toDirectoryEntry(@NotNull SVNURL url, @Nullable SVNURL repositoryUrl) throws SVNException {
      return new DirectoryEntry(url.appendPath(name, false), repositoryUrl, PathUtil.getFileName(name), kind,
                                commit != null ? commit.build() : null, name);
    }
  }
}
