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
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.*;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 5:21 PM
 */
public class CmdStatusClient extends BaseSvnClient implements StatusClient {

  @Override
  public long doStatus(@NotNull final File path,
                       @Nullable final SVNRevision revision,
                       @NotNull final Depth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       @NotNull final StatusConsumer handler,
                       @Nullable final Collection changeLists) throws SvnBindException {
    File base = CommandUtil.requireExistingParent(path);
    final Info infoBase = myFactory.createInfoClient().doInfo(base, revision);
    List<String> parameters = new ArrayList<>();

    putParameters(parameters, path, depth, remote, reportAll, includeIgnored, changeLists);

    CommandExecutor command = execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.st, parameters, null);
    parseResult(path, revision, handler, base, infoBase, command);
    return 0;
  }

  private void parseResult(final File path,
                           SVNRevision revision,
                           StatusConsumer handler,
                           File base,
                           Info infoBase,
                           CommandExecutor command) throws SvnBindException {
    String result = command.getOutput();

    if (StringUtil.isEmptyOrSpaces(result)) {
      throw new SvnBindException("Status request returned nothing for command: " + command.getCommandText());
    }

    try {
      final SvnStatusHandler[] svnHandl = new SvnStatusHandler[1];
      svnHandl[0] = createStatusHandler(revision, handler, base, infoBase, svnHandl);
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new ByteArrayInputStream(result.trim().getBytes(CharsetToolkit.UTF8_CHARSET)), svnHandl[0]);
      if (!svnHandl[0].isAnythingReported()) {
        if (!SvnUtil.isSvnVersioned(myVcs, path)) {
          throw new SvnBindException(SVNErrorCode.WC_NOT_DIRECTORY, "Command - " + command.getCommandText() + ". Result - " + result);
        } else {
          // return status indicating "NORMAL" state
          // typical output would be like
          // <status>
          // <target path="1.txt"></target>
          // </status>
          // so it does not contain any <entry> element and current parsing logic returns null

          PortableStatus status = new PortableStatus();
          status.setFile(path);
          status.setPath(path.getAbsolutePath());
          status.setContentsStatus(StatusType.STATUS_NORMAL);
          status.setInfoGetter(new Getter<Info>() {
            @Override
            public Info get() {
              return createInfoGetter(null).convert(path);
            }
          });
          try {
            handler.consume(status);
          }
          catch (SVNException e) {
            throw new SvnBindException(e);
          }
        }
      }
    }
    catch (SvnExceptionWrapper e) {
      throw new SvnBindException(e.getCause());
    } catch (IOException e) {
      throw new SvnBindException(e);
    }
    catch (ParserConfigurationException e) {
      throw new SvnBindException(e);
    }
    catch (SAXException e) {
      // status parsing errors are logged separately as sometimes there are parsing errors connected to terminal output handling.
      // these errors primarily occur when status output is rather large.
      // and status output could be large, for instance, when working copy is locked (seems that each file is listed in status output).
      command.logCommand();
      throw new SvnBindException(e);
    }
  }

  private static void putParameters(@NotNull List<String> parameters,
                                    @NotNull File path,
                                    @Nullable Depth depth,
                                    boolean remote,
                                    boolean reportAll,
                                    boolean includeIgnored,
                                    @Nullable Collection changeLists) {
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, remote, "-u");
    CommandUtil.put(parameters, reportAll, "--verbose");
    CommandUtil.put(parameters, includeIgnored, "--no-ignore");
    // TODO: Fix this check - update corresponding parameters in StatusClient
    CommandUtil.putChangeLists(parameters, changeLists);
    parameters.add("--xml");
  }

  public SvnStatusHandler createStatusHandler(final SVNRevision revision,
                                               final StatusConsumer handler,
                                               final File base,
                                               final Info infoBase, final SvnStatusHandler[] svnHandl) {
    final SvnStatusHandler.ExternalDataCallback callback = createStatusCallback(handler, base, infoBase, svnHandl);

    return new SvnStatusHandler(callback, base, createInfoGetter(revision));
  }

  private Convertor<File, Info> createInfoGetter(final SVNRevision revision) {
    return new Convertor<File, Info>() {
      @Override
      public Info convert(File o) {
        try {
          return myFactory.createInfoClient().doInfo(o, revision);
        }
        catch (SvnBindException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    };
  }

  public static SvnStatusHandler.ExternalDataCallback createStatusCallback(final StatusConsumer handler,
                                                                            final File base,
                                                                            final Info infoBase,
                                                                            final SvnStatusHandler[] svnHandl) {
    final Map<File, Info> externalsMap = new HashMap<>();
    final String[] changelistName = new String[1];

    return new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        final PortableStatus pending = svnHandl[0].getPending();
        pending.setChangelistName(changelistName[0]);
        try {
          //if (infoBase != null) {
          Info baseInfo = infoBase;
          File baseFile = base;
          final File pendingFile = new File(pending.getPath());
          if (! externalsMap.isEmpty()) {
            for (File file : externalsMap.keySet()) {
              if (FileUtil.isAncestor(file, pendingFile, false)) {
                baseInfo = externalsMap.get(file);
                baseFile = file;
                break;
              }
            }
          }
          if (baseInfo != null) {
            final String append;
            final String systemIndependentPath = FileUtil.toSystemIndependentName(pending.getPath());
            if (pendingFile.isAbsolute()) {
              final String relativePath =
                FileUtil.getRelativePath(FileUtil.toSystemIndependentName(baseFile.getPath()), systemIndependentPath, '/');
              append = SVNPathUtil.append(baseInfo.getURL().toString(), FileUtil.toSystemIndependentName(relativePath));
            }
            else {
              append = SVNPathUtil.append(baseInfo.getURL().toString(), systemIndependentPath);
            }
            pending.setURL(SVNURL.parseURIEncoded(append));
          }
          if (StatusType.STATUS_EXTERNAL.equals(pending.getNodeStatus())) {
            externalsMap.put(pending.getFile(), pending.getInfo());
          }
          handler.consume(pending);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }

      @Override
      public void switchChangeList(String newList) {
        changelistName[0] = newList;
      }
    };
  }

  @Override
  public Status doStatus(@NotNull File path, boolean remote) throws SvnBindException {
    final Status[] svnStatus = new Status[1];
    doStatus(path, SVNRevision.UNDEFINED, Depth.EMPTY, remote, false, false, false, new StatusConsumer() {
      @Override
      public void consume(Status status) throws SVNException {
        svnStatus[0] = status;
      }
    }, null);
    return svnStatus[0];
  }
}
