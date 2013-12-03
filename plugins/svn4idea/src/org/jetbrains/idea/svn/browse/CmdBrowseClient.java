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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdBrowseClient extends BaseSvnClient implements BrowseClient {

  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNDirEntryHandler handler) throws VcsException {
    assertUrl(target);

    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth);
    parameters.add("--xml");

    CommandExecutor command = CommandUtil.execute(myVcs, target, SvnCommandName.list, parameters, null);

    try {
      parseOutput(target.getURL(), command, handler);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  private static void parseOutput(@NotNull SVNURL url, @NotNull CommandExecutor command, @Nullable ISVNDirEntryHandler handler)
    throws VcsException, SVNException {
    try {
      TargetLists lists = CommandUtil.parse(command.getOutput(), TargetLists.class);

      if (handler != null && lists != null) {
        for (TargetList list : lists.lists) {
          for (Entry entry : list.entries) {
            handler.handleDirEntry(entry.toDirEntry(url));
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
    public List<TargetList> lists = new ArrayList<TargetList>();
  }

  public static class TargetList {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "entry")
    public List<Entry> entries = new ArrayList<Entry>();
  }

  public static class Entry {

    @XmlAttribute(name = "kind")
    public String kind;

    @XmlElement(name = "name")
    public String name;

    @XmlElement(name = "size")
    public long size;

    @XmlElement(name = "commit")
    public Commit commit;

    @XmlElement(name = "lock")
    public Lock lock;

    public long revision() {
      return commit != null ? commit.revision : 0;
    }

    public String author() {
      return commit != null ? commit.author : "";
    }

    public Date date() {
      return commit != null ? commit.date : null;
    }

    public SVNDirEntry toDirEntry(@NotNull SVNURL url) throws SVNException {
      // TODO: repository root and relative path are not used for now
      SVNDirEntry entry =
        new SVNDirEntry(url.appendPath(name, false), null, name, SVNNodeKind.parseKind(kind), size, false, revision(), date(),
                        author());

      entry.setRelativePath(null);
      entry.setLock(lock != null ? lock.toLock(entry.getRelativePath()) : null);

      return entry;
    }
  }

  public static class Commit {

    @XmlAttribute(name = "revision")
    public long revision;

    @XmlElement(name = "author")
    public String author;

    @XmlElement(name = "date")
    public Date date;
  }

  public static class Lock {

    @XmlElement(name = "token")
    public String token;

    @XmlElement(name = "owner")
    public String owner;

    @XmlElement(name = "comment")
    public String comment;

    @XmlElement(name = "created")
    public Date created;

    @XmlElement(name = "expires")
    public Date expires;

    public SVNLock toLock(@NotNull String path) {
      return new SVNLock(path, token, owner, comment, created, expires);
    }
  }
}
