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
package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.LineCommandListener;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnLineCommand;
import org.jetbrains.idea.svn.config.SvnBindException;
import org.tigris.subversion.javahl.BindClientException;
import org.tigris.subversion.javahl.ClientException;

import java.io.File;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 4:56 PM
 */
public class SvnCommitRunner {
  private final static String ourTransmittingData = "Transmitting file data";
  private final static String ourCommittedRevision = "Committed revision";

  private SvnBindException myException;
  private final String myExePath;
  @Nullable private final CommitEventHandler myHandler;
  @Nullable private final AuthenticationCallback myAuthenticationCallback;
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnCommitRunner");
  private long myCommittedRevision = -1;

  public SvnCommitRunner(String path, @Nullable CommitEventHandler handler, @Nullable AuthenticationCallback authenticationCallback) {
    myExePath = path;
    myHandler = handler;
    myAuthenticationCallback = authenticationCallback;
  }

  public long commit(String[] paths,
                     String message,
                     int depth,
                     boolean noUnlock,
                     boolean keepChangelist,
                     String[] changelists,
                     Map revpropTable) throws ClientException {
    if (paths.length == 0) return -1;

    final List<String> parameters = new ArrayList<String>();
    parameters.add("--depth");
    parameters.add(Util.getDepthName(depth));
    if (noUnlock) {
      parameters.add("--no-unlock");
    }
    if (keepChangelist) {
      parameters.add("--keep-changelists");
    }
    if (changelists != null && changelists.length > 0) {
      Util.changelistsToCommand(changelists, parameters);
    }
    if (revpropTable != null && ! revpropTable.isEmpty()) {
      final Set<Map.Entry<Object, Object>> set = revpropTable.entrySet();
      for (Map.Entry<Object, Object> entry : set) {
        parameters.add("--with-revprop");
        parameters.add(entry.getKey() + "=" + entry.getValue());
      }
    }
    parameters.add("-m");
    parameters.add(message);
    Arrays.sort(paths);
    parameters.addAll(Arrays.asList(paths));

    try {
      SvnLineCommand.runAndWaitProcessErrorsIntoExceptions(myExePath, new File(paths[0]), SvnCommandName.ci,
                                                           new CommandListener(), myAuthenticationCallback, ArrayUtil.toStringArray(parameters));
    }
    catch (SvnBindException e) {
      throw BindClientException.create(e, -1);
    }
    if (myException != null) {
      throw BindClientException.create(myException, -1);
    }

    return myCommittedRevision;
  }

  private class CommandListener extends LineCommandListener {
    private File myBase;

    @Override
    public void baseDirectory(File file) {
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
      if (line.startsWith(ourTransmittingData)) {
        if (myHandler != null) {
          myHandler.commitEvent(CommitEventType.transmittingDeltas, myBase);
        }
        return;
      }
      if (line.startsWith(ourCommittedRevision)) {
        final String substring = line.substring(ourCommittedRevision.length());
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
        final int idxSpace = line.indexOf(' ');
        if (idxSpace == -1) {
          LOG.info("Can not parse event type: " + line);
          return;
        }
        final CommitEventType type = CommitEventType.create(line.substring(0, idxSpace));
        if (type == null) {
          LOG.info("Can not parse event type: " + line);
          return;
        }
        final File target = new File(myBase, new String(line.substring(idxSpace + 1).trim()));
        myHandler.commitEvent(type, target);
      }
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
