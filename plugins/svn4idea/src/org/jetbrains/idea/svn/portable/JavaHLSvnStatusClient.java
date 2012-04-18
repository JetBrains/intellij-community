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
package org.jetbrains.idea.svn.portable;

import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.SVNClient;
import org.apache.subversion.javahl.callback.InfoCallback;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Info;
import org.apache.subversion.javahl.types.Revision;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 9:51 AM
 */
public class JavaHLSvnStatusClient implements SvnStatusClientI {
  private final Project myProject;

  public JavaHLSvnStatusClient(final Project project) {
    myProject = project;
  }

  @Override
  public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
    throws SVNException {
    return doStatus(path, SVNRevision.UNDEFINED, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, remote, reportAll, includeIgnored,
                    false, handler, null);
  }

  @Override
  public long doStatus(File path,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return doStatus(path, SVNRevision.UNDEFINED, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, remote, reportAll, includeIgnored,
                    collectParentExternals, handler, null);
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
  public long doStatus(File path,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler,
                       Collection changeLists) throws SVNException {
    final SVNException[] exc = new SVNException[1];
    SVNClient client = new SVNClient();
    try {
      client.status(path.getPath(), DepthConvertor.convert(depth), remote, reportAll, !includeIgnored, !collectParentExternals,
                    changeLists, StatusCallbackConvertor.create(handler, new Convertor<String, SVNInfo>() {
                                                                  @Override
                                                                  public SVNInfo convert(String o) {
                                                                    final SVNInfo[] infoArr = new SVNInfo[1];
                                                                    try {
                                                                      new SVNClient().info2(o, Revision.START, Revision.START, Depth.empty, null, new InfoCallback() {
                                                                        @Override
                                                                        public void singleInfo(Info info) {
                                                                          try {
                                                                            infoArr[0] = InfoConvertor.convert(info);
                                                                          }
                                                                          catch (SVNException e) {
                                                                            throw new SvnExceptionWrapper(e);
                                                                          }
                                                                        }
                                                                      });
                                                                    }
                                                                    catch (ClientException e) {
                                                                      throw new SvnExceptionWrapper(ExceptionConvertor.convert(e));
                                                                    }
                                                                    return infoArr[0];
                                                                  }
                                                                }, new Consumer<SVNException>() {
                                                                  @Override
                                                                  public void consume(SVNException e) {
                                                                    exc[0] = e;
                                                                  }
                                                                }));
    }
    catch (ClientException e) {
      throw ExceptionConvertor.convert(e);
    }
    if (exc[0] != null) {
      throw exc[0];
    }
    // isn't used
    return -1;
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote) throws SVNException {
    return doStatus(path, remote, false);
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
    final SVNStatus[] statusArr = new SVNStatus[0];
    doStatus(path, SVNRevision.UNDEFINED, SVNDepth.EMPTY, remote, false, false, collectParentExternals,
             new ISVNStatusHandler() {
               @Override
               public void handleStatus(SVNStatus status) throws SVNException {
                 statusArr[0] = status;
               }
             }, null);
    return statusArr[0];
  }
}
