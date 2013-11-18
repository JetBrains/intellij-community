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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.portable.PortableStatus;
import org.jetbrains.idea.svn.portable.SvnExceptionWrapper;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.*;
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
public class SvnCommandLineStatusClient implements SvnStatusClientI {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient");

  private final SvnCommandLineInfoClient myInfoClient;
  @NotNull private final SvnVcs myVcs;

  public SvnCommandLineStatusClient(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myInfoClient = new SvnCommandLineInfoClient(vcs);
  }

  @Override
  public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
    throws SVNException {
    return doStatus(path, recursive, remote, reportAll, includeIgnored, false, handler);
  }

  @Override
  public long doStatus(File path,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return doStatus(path, SVNRevision.UNDEFINED, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler);
  }

  @Override
  public long doStatus(File path,
                       SVNRevision revision,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return doStatus(path, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, remote, reportAll, includeIgnored,
                    collectParentExternals, handler, null);
  }

  @Override
  public long doStatus(final File path,
                       final SVNRevision revision,
                       final SVNDepth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       final ISVNStatusHandler handler,
                       final Collection changeLists) throws SVNException {
    File base = path.isDirectory() ? path : path.getParentFile();
    base = CommandUtil.correctUpToExistingParent(base);

    final SVNInfo infoBase = myInfoClient.doInfo(base, revision);
    List<String> parameters = new ArrayList<String>();

    putParameters(parameters, path, depth, remote, reportAll, includeIgnored, changeLists);

    CommandExecutor command;
    try {
      command = CommandUtil.execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.st, parameters, null);
    }
    catch (VcsException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
    parseResult(path, revision, handler, base, infoBase, command);
    return 0;
  }

  private void parseResult(final File path,
                           SVNRevision revision,
                           ISVNStatusHandler handler,
                           File base,
                           SVNInfo infoBase,
                           CommandExecutor command) throws SVNException {
    String result = command.getOutput();

    if (StringUtil.isEmptyOrSpaces(result)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Status request returned nothing for command: " +
                                                                             command.myCommandLine.getCommandLineString()));
    }

    try {
      final SvnStatusHandler[] svnHandl = new SvnStatusHandler[1];
      svnHandl[0] = createStatusHandler(revision, handler, base, infoBase, svnHandl);
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new ByteArrayInputStream(result.getBytes(CharsetToolkit.UTF8_CHARSET)), svnHandl[0]);
      if (!svnHandl[0].isAnythingReported()) {
        if (!SvnUtil.isSvnVersioned(myVcs, path)) {
          throw new SVNException(
            SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Command - " + command.getCommandText() + ". Result - " + result));
        } else {
          // return status indicating "NORMAL" state
          // typical output would be like
          // <status>
          // <target path="1.txt"></target>
          // </status>
          // so it does not contain any <entry> element and current parsing logic returns null

          PortableStatus status = new PortableStatus();
          status.setPath(path.getAbsolutePath());
          status.setContentsStatus(SVNStatusType.STATUS_NORMAL);
          status.setInfoGetter(new Getter<SVNInfo>() {
            @Override
            public SVNInfo get() {
              return createInfoGetter(null).convert(path);
            }
          });
          handler.handleStatus(status);
        }
      }
    }
    catch (SvnExceptionWrapper e) {
      throw (SVNException) e.getCause();
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
    catch (ParserConfigurationException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
    catch (SAXException e) {
      // status parsing errors are logged separately as sometimes there are parsing errors connected to terminal output handling.
      // these errors primarily occur when status output is rather large.
      // and status output could be large, for instance, when working copy is locked (seems that each file is listed in status output).
      command.logCommand();

      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
  }

  private static void putParameters(@NotNull List<String> parameters,
                                    @NotNull File path,
                                    @Nullable SVNDepth depth,
                                    boolean remote,
                                    boolean reportAll,
                                    boolean includeIgnored,
                                    @Nullable Collection changeLists) {
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, remote, "-u");
    CommandUtil.put(parameters, reportAll, "--verbose");
    CommandUtil.put(parameters, includeIgnored, "--no-ignore");
    // TODO: Fix this check - update corresponding parameters in SvnStatusClientI
    CommandUtil.putChangeLists(parameters, changeLists);
    parameters.add("--xml");
  }

  public SvnStatusHandler createStatusHandler(final SVNRevision revision,
                                               final ISVNStatusHandler handler,
                                               final File base,
                                               final SVNInfo infoBase, final SvnStatusHandler[] svnHandl) {
    final SvnStatusHandler.ExternalDataCallback callback = createStatusCallback(handler, base, infoBase, svnHandl);

    return new SvnStatusHandler(callback, base, createInfoGetter(revision));
  }

  private Convertor<File, SVNInfo> createInfoGetter(final SVNRevision revision) {
    return new Convertor<File, SVNInfo>() {
      @Override
      public SVNInfo convert(File o) {
        try {
          return myInfoClient.doInfo(o, revision);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    };
  }

  public static SvnStatusHandler.ExternalDataCallback createStatusCallback(final ISVNStatusHandler handler,
                                                                            final File base,
                                                                            final SVNInfo infoBase,
                                                                            final SvnStatusHandler[] svnHandl) {
    final Map<File, SVNInfo> externalsMap = new HashMap<File, SVNInfo>();
    final String[] changelistName = new String[1];

    return new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        final PortableStatus pending = svnHandl[0].getPending();
        pending.setChangelistName(changelistName[0]);
        try {
          //if (infoBase != null) {
          SVNInfo baseInfo = infoBase;
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
          if (SVNStatusType.STATUS_EXTERNAL.equals(pending.getNodeStatus())) {
            externalsMap.put(pending.getFile(), pending.getInfo());
          }
          handler.handleStatus(pending);
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
  public SVNStatus doStatus(File path, boolean remote) throws SVNException {
    return doStatus(path, remote, false);
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
    final SVNStatus[] svnStatus = new SVNStatus[1];
    doStatus(path, SVNRevision.UNDEFINED, SVNDepth.EMPTY, remote, false, false, collectParentExternals, new ISVNStatusHandler() {
      @Override
      public void handleStatus(SVNStatus status) throws SVNException {
        svnStatus[0] = status;
      }
    }, null);
    return svnStatus[0];
  }
}
