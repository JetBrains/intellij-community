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
package org.jetbrains.idea.svn.info;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 12:59 PM
 */
public class CmdInfoClient extends BaseSvnClient implements InfoClient {

  private static final Logger LOG = Logger.getInstance(CmdInfoClient.class);

  private String execute(@NotNull List<String> parameters, @NotNull File path) throws SvnBindException {
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
      CommandExecutor command = execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.info, parameters, listener);

      return command.getOutput();
    }
    catch (SvnBindException e) {
      final String text = StringUtil.notNullize(e.getMessage());
      if (text.contains("W155010")) {
        // if "svn info" is executed for several files at once, then this warning could be printed only for some files, but info for other
        // files should be parsed from output
        return output.getStdout();
      }
      // not a working copy exception
      // "E155007: '' is not a working copy"
      if (text.contains("is not a working copy") && StringUtil.isNotEmpty(output.getStdout())) {
        // TODO: Seems not reproducible in 1.8.4
        // workaround: as in subversion 1.8 "svn info" on a working copy root outputs such error for parent folder,
        // if there are files with conflicts.
        // but the requested info is still in the output except root closing tag
        return output.getStdout() + "</info>";
      }
      throw e;
    }
  }

  @Nullable
  private static Info parseResult(@Nullable File base, @Nullable String result) throws SvnBindException {
    CollectInfoHandler handler = new CollectInfoHandler();

    parseResult(handler, base, result);

    return handler.getInfo();
  }

  private static void parseResult(@NotNull final InfoConsumer handler, @Nullable File base, @Nullable String result)
    throws SvnBindException {
    if (StringUtil.isEmptyOrSpaces(result)) {
      return;
    }

    final SvnInfoHandler infoHandler = new SvnInfoHandler(base, new Consumer<Info>() {
      @Override
      public void consume(Info info) {
        try {
          handler.consume(info);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    });

    parseResult(result, infoHandler);
  }

  private static void parseResult(@NotNull String result, @NotNull SvnInfoHandler handler) throws SvnBindException {
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

      parser.parse(new ByteArrayInputStream(result.trim().getBytes(CharsetToolkit.UTF8_CHARSET)), handler);
    }
    catch (SvnExceptionWrapper e) {
      LOG.info("info output " + result);
      throw new SvnBindException(e.getCause());
    } catch (IOException e) {
      LOG.info("info output " + result);
      throw new SvnBindException(e);
    }
    catch (ParserConfigurationException e) {
      LOG.info("info output " + result);
      throw new SvnBindException(e);
    }
    catch (SAXException e) {
      LOG.info("info output " + result);
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private static List<String> buildParameters(@NotNull String path,
                                              @Nullable SVNRevision pegRevision,
                                              @Nullable SVNRevision revision,
                                              @Nullable Depth depth) {
    List<String> parameters = ContainerUtil.newArrayList();

    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, path, pegRevision);
    parameters.add("--xml");

    return parameters;
  }

  @Override
  public Info doInfo(File path, SVNRevision revision) throws SvnBindException {
    File base = path.isDirectory() ? path : path.getParentFile();
    base = CommandUtil.correctUpToExistingParent(base);
    if (base == null) {
      // very unrealistic
      throw new SvnBindException("Can not find existing parent file");
    }

    return parseResult(base, execute(buildParameters(path.getAbsolutePath(), SVNRevision.UNDEFINED, revision, Depth.EMPTY), path));
  }

  @Override
  public Info doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SvnBindException {
    CommandExecutor command =
      execute(myVcs, SvnTarget.fromURL(url), SvnCommandName.info, buildParameters(url.toString(), pegRevision, revision, Depth.EMPTY),
              null);

    return parseResult(null, command.getOutput());
  }

  @Override
  public void doInfo(@NotNull Collection<File> paths, @Nullable InfoConsumer handler) throws SvnBindException {
    File base = ContainerUtil.getFirstItem(paths);

    if (base != null) {
      base = CommandUtil.correctUpToExistingParent(base);

      List<String> parameters = ContainerUtil.newArrayList();
      for (File file : paths) {
        CommandUtil.put(parameters, file);
      }
      parameters.add("--xml");

      // Currently do not handle exceptions here like in SvnVcs.handleInfoException - just continue with parsing in case of warnings for
      // some of the requested items
      String result = execute(parameters, base);
      if (handler != null) {
        parseResult(handler, base, result);
      }
    }
  }

  private static class CollectInfoHandler implements InfoConsumer {

    @Nullable private Info myInfo;

    @Override
    public void consume(Info info) throws SVNException {
      myInfo = info;
    }

    @Nullable
    public Info getInfo() {
      return myInfo;
    }
  }
}
