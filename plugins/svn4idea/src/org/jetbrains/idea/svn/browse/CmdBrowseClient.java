// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.browse;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.lock.Lock;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.append;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdBrowseClient extends BaseSvnClient implements BrowseClient {

  @Override
  public void list(@NotNull Target target,
                   @Nullable Revision revision,
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

    parseOutput(target.getUrl(), command, handler, info != null ? info.getRepositoryRootURL() : null);
  }

  @Override
  public long createDirectory(@NotNull Target target, @NotNull String message, boolean makeParents) throws VcsException {
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

  private static void parseOutput(@NotNull Url url,
                                  @NotNull CommandExecutor command,
                                  @Nullable DirectoryEntryConsumer handler,
                                  @Nullable Url repositoryUrl) throws SvnBindException {
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
    public DirectoryEntry toDirectoryEntry(@NotNull Url url, @Nullable Url repositoryUrl) throws SvnBindException {
      return new DirectoryEntry(append(url, name), repositoryUrl, PathUtil.getFileName(name), kind, commit != null ? commit.build() : null,
                                name);
    }
  }
}
