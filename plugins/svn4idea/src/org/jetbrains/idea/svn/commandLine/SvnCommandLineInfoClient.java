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

import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.portable.SvnExceptionWrapper;
import org.jetbrains.idea.svn.portable.SvnkitSvnWcClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 12:59 PM
 */
public class SvnCommandLineInfoClient extends SvnkitSvnWcClient {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.commandLine.SvnCommandLineInfoClient");

  @NotNull private final SvnVcs myVcs;

  public SvnCommandLineInfoClient(@NotNull final SvnVcs vcs) {
    super(vcs);
    myVcs = vcs;
  }

  @Override
  public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
    doInfo(path, SVNRevision.UNDEFINED, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(path, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path,
                     SVNRevision pegRevision,
                     SVNRevision revision,
                     SVNDepth depth,
                     Collection changeLists,
                     final ISVNInfoHandler handler) throws SVNException {
    File base = path.isDirectory() ? path : path.getParentFile();
    base = CommandUtil.correctUpToExistingParent(base);
    if (base == null) {
      // very unrealistic
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not find existing parent file"));
    }
    issueCommand(path, pegRevision, revision, depth, changeLists, handler, base);
  }

  private void issueCommand(File path, SVNRevision pegRevision,
                            SVNRevision revision,
                            SVNDepth depth,
                            Collection changeLists,
                            final ISVNInfoHandler handler, File base) throws SVNException {
    List<String> parameters = new ArrayList<String>();

    fillParameters(path.getAbsolutePath(), pegRevision, revision, depth, parameters);
    // TODO: Fix this check - update corresponding parameters in SvnWcClientI
    CommandUtil.putChangeLists(parameters, changeLists);

    parseResult(handler, base, execute(parameters, path));
  }

  private String execute(@NotNull List<String> parameters, @NotNull File path) throws SVNException {
    // workaround: separately capture command output - used in exception handling logic to overcome svn 1.8 issue (see below)
    final ProcessOutput output = new ProcessOutput();
    LineCommandListener listener = new LineCommandAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          output.appendStdout(line);
        }
      }
    };

    try {
      CommandExecutor command = CommandUtil.execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.info, parameters, listener);

      return command.getOutput();
    }
    catch (VcsException e) {
      final String text = e.getMessage();
      final boolean notEmpty = !StringUtil.isEmptyOrSpaces(text);
      if (notEmpty && text.contains("W155010")) {
        // just null
        return null;
      }
      // not a working copy exception
      // "E155007: '' is not a working copy"
      if (notEmpty && text.contains("is not a working copy")) {
        if (StringUtil.isNotEmpty(output.getStdout())) {
          // workaround: as in subversion 1.8 "svn info" on a working copy root outputs such error for parent folder,
          // if there are files with conflicts.
          // but the requested info is still in the output except root closing tag
          return output.getStdout() + "</info>";
        } else {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, e), e);
        }
      // svn: E200009: Could not display info for all targets because some targets don't exist
      } else if (notEmpty && text.contains("some targets don't exist")) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, e), e);
      } else if (notEmpty && text.contains(String.valueOf(SVNErrorCode.WC_UPGRADE_REQUIRED.getCode()))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_UPGRADE_REQUIRED, e), e);
      } else if (notEmpty &&
                 (text.contains("upgrade your Subversion client") ||
                  text.contains(String.valueOf(SVNErrorCode.WC_UNSUPPORTED_FORMAT.getCode())))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, e), e);
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
  }

  private void parseResult(@NotNull final ISVNInfoHandler handler, @Nullable File base, @Nullable String result) throws SVNException {
    if (StringUtil.isEmpty(result)) {
      return;
    }

    final SvnInfoHandler[] infoHandler = new SvnInfoHandler[1];
    infoHandler[0] = new SvnInfoHandler(base, new Consumer<SVNInfo>() {
      @Override
      public void consume(SVNInfo info) {
        try {
          handler.handleInfo(info);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    });

    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

      parser.parse(new ByteArrayInputStream(result.getBytes(CharsetToolkit.UTF8_CHARSET)), infoHandler[0]);
    }
    catch (SvnExceptionWrapper e) {
      LOG.info("info output " + result);
      throw (SVNException) e.getCause();
    } catch (IOException e) {
      LOG.info("info output " + result);
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
    catch (ParserConfigurationException e) {
      LOG.info("info output " + result);
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
    catch (SAXException e) {
      LOG.info("info output " + result);
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
  }

  private void fillParameters(String path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, List<String> parameters) {
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, path, pegRevision);
    parameters.add("--xml");
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(url, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, handler);
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, ISVNInfoHandler handler)
    throws SVNException {
    String path = url.toDecodedString();
    List<String> parameters = new ArrayList<String>();

    fillParameters(path, pegRevision, revision, depth, parameters);
    CommandExecutor command;
    try {
      command = CommandUtil.execute(myVcs, SvnTarget.fromURL(url), SvnCommandName.info, parameters, null);
    }
    catch (VcsException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }

    parseResult(handler, null, command.getOutput());
  }

  @Override
  public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
    final SVNInfo[] infoArr = new SVNInfo[1];
    doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.EMPTY, null, new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        infoArr[0] = info;
      }
    });
    return infoArr[0];
  }

  @Override
  public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    final SVNInfo[] infoArr = new SVNInfo[1];
    doInfo(url, pegRevision, revision, SVNDepth.EMPTY, new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        infoArr[0] = info;
      }
    });
    return infoArr[0];
  }
}
