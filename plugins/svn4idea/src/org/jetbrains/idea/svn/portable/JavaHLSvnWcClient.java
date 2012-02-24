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

import com.intellij.util.Consumer;
import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.SVNClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 6:53 PM
 */
public class JavaHLSvnWcClient extends SvnkitSvnWcClient {
  public JavaHLSvnWcClient(SVNWCClient client) {
    super(client);
  }

  @Override
  public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(path, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
    doInfo(path, SVNRevision.UNDEFINED, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path,
                     SVNRevision pegRevision,
                     SVNRevision revision,
                     SVNDepth depth,
                     Collection changeLists,
                     ISVNInfoHandler handler) throws SVNException {
    final SVNException[] exc = new SVNException[1];
    try {
      new SVNClient().info2(path.getPath(), RevisionConvertor.convert(revision), RevisionConvertor.convert(pegRevision),
                            DepthConvertor.convert(depth), null, InfoCallbackConvertor.create(handler,
                            new Consumer<SVNException>() {
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
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(url, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, handler);
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, ISVNInfoHandler handler)
    throws SVNException {
    final SVNException[] exc = new SVNException[1];
    try {
      new SVNClient().info2(url.getPath(), RevisionConvertor.convert(revision), RevisionConvertor.convert(pegRevision),
                            DepthConvertor.convert(depth), null, InfoCallbackConvertor.create(handler, 
                            new Consumer<SVNException>() {
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
  }

  @Override
  public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
    final SVNInfo[] infoArr = new SVNInfo[1];
    doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.INFINITY, null, new ISVNInfoHandler() {
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
    doInfo(url, pegRevision, revision, SVNDepth.INFINITY, new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        infoArr[0] = info;
      }
    });
    return infoArr[0];
  }
}
