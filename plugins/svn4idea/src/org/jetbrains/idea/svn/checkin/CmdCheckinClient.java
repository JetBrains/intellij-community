// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractFilterChildren;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.*;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class CmdCheckinClient extends BaseSvnClient implements CheckinClient {

  private static final Logger LOG = Logger.getInstance(CmdCheckinClient.class);

  public static final long INVALID_REVISION_NUMBER = -1L;

  @Override
  public CommitInfo @NotNull [] commit(@NotNull List<File> paths, @NotNull String message) throws VcsException {
    // if directory renames were used, IDEA reports all files under them as moved, but for svn we can not pass some of them
    // to commit command - since not all paths are registered as changes -> so we need to filter these cases, but only if
    // there at least some child-parent relationships in passed paths
    paths = filterCommittables(paths);

    return runCommit(paths, message);
  }

  private CommitInfo @NotNull [] runCommit(@NotNull List<File> paths, @NotNull String message) throws VcsException {
    if (ContainerUtil.isEmpty(paths)) return new CommitInfo[]{CommitInfo.EMPTY};

    Command command = newCommand(SvnCommandName.ci);

    command.put(Depth.EMPTY);
    if (SvnConfiguration.getInstance(myVcs.getProject()).isKeepLocks()) {
      command.put("--no-unlock");
    }
    command.put("-m", message);
    // TODO: seems that sort is not necessary here
    ContainerUtil.sort(paths);
    command.setTargets(paths);

    IdeaCommitHandler handler = new IdeaCommitHandler(ProgressManager.getInstance().getProgressIndicator());
    CmdCheckinClient.CommandListener listener = new CommandListener(handler);
    listener.setBaseDirectory(CommandUtil.requireExistingParent(paths.get(0)));
    execute(myVcs, Target.on(paths.get(0)), null, command, listener);
    listener.throwExceptionIfOccurred();

    long revision = validateRevisionNumber(listener.getCommittedRevision());

    return new CommitInfo[]{new CommitInfo.Builder().setRevisionNumber(revision).build()};
  }

  private static long validateRevisionNumber(long revision) throws VcsException {
    if (revision < 0) {
      throw new VcsException(message("error.wrong.committed.revision.number", revision));
    }

    return revision;
  }

  @NotNull
  private List<File> filterCommittables(@NotNull List<File> committables) throws SvnBindException {
    final Set<String> childrenOfSomebody = new HashSet<>();
    new AbstractFilterChildren<File>() {
      @Override
      protected void sortAscending(List<? extends File> list) {
        Collections.sort(list);
      }

      @Override
      protected boolean isAncestor(File parent, File child) {
        // strict here will ensure that for case insensitive file systems paths different only by case will not be treated as ancestors
        // of each other which is what we need to perform renames from one case to another on Windows
        final boolean isAncestor = FileUtil.isAncestor(parent, child, true);
        if (isAncestor) {
          childrenOfSomebody.add(child.getPath());
        }
        return isAncestor;
      }
    }.doFilter(new ArrayList<>(committables));
    if (!childrenOfSomebody.isEmpty()) {
      List<File> result = new ArrayList<>();
      StatusClient statusClient = myFactory.createStatusClient();

      for (File file : committables) {
        if (!childrenOfSomebody.contains(file.getPath())) {
          result.add(file);
        }
        else {
          try {
            final Status status = statusClient.doStatus(file, false);
            if (status != null && !status.is(StatusType.STATUS_NONE, StatusType.STATUS_UNVERSIONED)) {
              result.add(file);
            }
          }
          catch (SvnBindException e) {
            // not versioned
            LOG.info(e);
            throw e;
          }
        }
      }
      return result;
    }
    return committables;
  }

  public static class CommandListener extends LineCommandAdapter {

    // Status could contain spaces, like "Adding copy of   <path>". But at the end we are not interested in "copy of" part and want to have
    // only "Adding" in match group.
    private static final String STATUS = "\\s*(\\w+)(.*?)\\s\\s+";
    private static final String OPTIONAL_FILE_TYPE = "(\\(.*\\))?";
    private static final String PATH = "\\s*(.*?)\\s*";
    private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + OPTIONAL_FILE_TYPE + PATH);

    @Nullable private final CommitEventHandler myHandler;
    private SvnBindException myException;
    private long myCommittedRevision = INVALID_REVISION_NUMBER;
    private File myBase;

    public CommandListener(@Nullable CommitEventHandler handler) {
      myHandler = handler;
    }

    public void throwExceptionIfOccurred() throws VcsException {
      if (myException != null) {
        throw myException;
      }
    }

    public long getCommittedRevision() {
      return myCommittedRevision;
    }

    public void setBaseDirectory(@NotNull File file) {
      myBase = file;
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      final String trim = line.trim();
      if (ProcessOutputTypes.STDOUT.equals(outputType)) {
        try {
          parseLine(trim);
        }
        catch (SvnBindException e) {
          myException = e;
        }
      }
    }

    private void parseLine(@NlsSafe String line) throws SvnBindException {
      if (StringUtil.isEmptyOrSpaces(line)) return;
      if (line.startsWith(CommitEventType.transmittingDeltas.getText())) {
        if (myHandler != null) {
          myHandler.commitEvent(CommitEventType.transmittingDeltas, myBase);
        }
        return;
      }
      if (line.startsWith(CommitEventType.skipped.getText())) {
        File target = null;
        if (myHandler != null) {
          int pathStart = line.indexOf('\'');
          if (pathStart > -1) {
            int pathEnd = line.indexOf('\'', pathStart + 1);
            if (pathEnd > -1) {
              target = toFile(line.substring(pathStart + 1, pathEnd));
            }
          }
          if (target != null) {
            myHandler.commitEvent(CommitEventType.skipped, myBase);
          } else {
            LOG.info("Can not parse 'Skipped' path " + line);
          }
        }
        return;
      }
      if (line.startsWith(CommitEventType.committedRevision.getText())) {
        final String substring = line.substring(CommitEventType.committedRevision.getText().length());
        int cnt = 0;
        while (StringUtil.isWhiteSpace(substring.charAt(cnt))) {
          ++ cnt;
        }
        final StringBuilder num = new StringBuilder();
        while (Character.isDigit(substring.charAt(cnt))) {
          num.append(substring.charAt(cnt));
          ++ cnt;
        }
        if (num.length() > 0) {
          try {
            myCommittedRevision = Long.parseLong(num.toString());
            if (myHandler != null) {
              myHandler.committedRevision(myCommittedRevision);
            }
          } catch (NumberFormatException e) {
            LOG.info("Wrong committed revision number: " + num + ", " + line, e);
            throw new SvnBindException(message("error.wrong.committed.revision.number", num) + ", " + line);
          }
        } else {
          LOG.info("Missing committed revision number: " + num + ", " + line);
          throw new SvnBindException(message("error.missing.committed.revision.number", num) + ", " + line);
        }
      } else {
        if (myHandler == null) return;

        Matcher matcher = CHANGED_PATH.matcher(line);
        if (matcher.matches()) {
          final CommitEventType type = CommitEventType.create(matcher.group(1));
          if (type == null) {
            LOG.info("Can not parse event type: " + line);
            return;
          }
          myHandler.commitEvent(type, toFile(matcher.group(4)));
        } else {
          LOG.info("Can not parse output: " + line);
        }
      }
    }

    @NotNull
    private File toFile(@NotNull String path) {
      return SvnUtil.resolvePath(myBase, path);
    }
  }


/*C:\TestProjects\sortedProjects\Subversion\local2\preRelease\mod2\src\com\test>sv
  n st
  D       gggG
  D       gggG\Rrr.java
  D       gggG\and555.txt
  D       gggG\test.txt
  A  +    gggGA
  D  +    gggGA\Rrr.java
  A  +    gggGA\RrrAA.java
  D  +    gggGA\and.txt
  M  +    gggGA\and555.txt
  A       gggGA\someNewFile.txt

  --- Changelist 'New changelistrwerwe':
  A       ddd.jpg

  C:\TestProjects\sortedProjects\Subversion\local2\preRelease\mod2\src\com\test>sv
  n ci -m 123
  Adding         ddd.jpg
  Deleting       gggG
  Adding         gggGA
  Deleting       gggGA\Rrr.java
  Adding         gggGA\RrrAA.java
  Sending        gggGA\and555.txt
  Adding         gggGA\someNewFile.txt
  Transmitting file data ....
  Committed revision 165.*/
}
