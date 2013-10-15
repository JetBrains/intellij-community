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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.apache.subversion.javahl.types.Revision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 4:56 PM
 */
public class SvnCommitRunner {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.commandLine.SvnCommitRunner");

  private SvnCommitRunner.CommandListener myCommandListener;
  private SvnVcs myVcs;

  public SvnCommitRunner(@NotNull SvnVcs vcs, @Nullable CommitEventHandler handler) {
    myVcs = vcs;
    myCommandListener = new CommandListener(handler);
  }

  public long commit(File[] paths,
                     String message,
                     @Nullable SVNDepth depth,
                     boolean noUnlock,
                     boolean keepChangelist,
                     Collection<String> changelists,
                     Map revpropTable) throws VcsException {
    if (paths.length == 0) return Revision.SVN_INVALID_REVNUM;

    final List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, noUnlock, "--no-unlock");
    CommandUtil.put(parameters, keepChangelist, "--keep-changelists");
    CommandUtil.putChangeLists(parameters, changelists);

    if (revpropTable != null && ! revpropTable.isEmpty()) {
      final Set<Map.Entry<Object, Object>> set = revpropTable.entrySet();
      for (Map.Entry<Object, Object> entry : set) {
        parameters.add("--with-revprop");
        parameters.add(entry.getKey() + "=" + entry.getValue());
      }
    }
    parameters.add("-m");
    parameters.add(message);
    // TODO: seems that sort is not necessary here
    Arrays.sort(paths);
    CommandUtil.put(parameters, paths);

    myCommandListener.setBaseDirectory(CommandUtil.correctUpToExistingParent(paths[0]));
    CommandUtil.execute(myVcs, SvnTarget.fromFile(paths[0]), SvnCommandName.ci, parameters, myCommandListener);
    myCommandListener.throwExceptionIfOccurred();

    return validateRevisionNumber();
  }

  private long validateRevisionNumber() throws VcsException {
    long revision = myCommandListener.getCommittedRevision();

    if (revision < 0) {
      throw new VcsException("Wrong committed revision number: " + revision);
    }

    return revision;
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
    private long myCommittedRevision = Revision.SVN_INVALID_REVNUM;
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

    private void parseLine(String line) throws SvnBindException {
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
            final String message = "Wrong committed revision number: " + num.toString() + ", string: " + line;
            LOG.info(message, e);
            throw new SvnBindException(message);
          }
        } else {
          final String message = "Missing committed revision number: " + num.toString() + ", string: " + line;
          LOG.info(message);
          throw new SvnBindException(message);
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
      File result = new File(path);

      if (!result.isAbsolute()) {
        result = new File(myBase, result.getPath());
      }

      return result;
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
