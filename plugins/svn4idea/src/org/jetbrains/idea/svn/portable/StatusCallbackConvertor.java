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

import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import org.apache.subversion.javahl.callback.StatusCallback;
import org.apache.subversion.javahl.types.Status;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 9:55 AM
 */
public class StatusCallbackConvertor {
  public static StatusCallback create(final ISVNStatusHandler handler, final Convertor<String, SVNInfo> infoGetter,
                                      final Consumer<SVNException> exceptionConsumer) {
    return new StatusCallback() {
      @Override
      public void doStatus(String path, Status status) {
        if (handler == null) return;
        try {
          handler.handleStatus(convert(path, status, infoGetter));
        }
        catch (SVNException e) {
          exceptionConsumer.consume(e);
        }
      }
    };
  }
  
  public static SVNStatus convert(final String path, Status status, final Convertor<String, SVNInfo> infoGetter) throws SVNException {
      // no locks
    return new PortableStatus(createUrl(status.getUrl()), new File(path), NodeKindConvertor.convert(status.getNodeKind()),
                              RevisionConvertor.convert(status.getRevision()), RevisionConvertor.convert(status.getLastChangedRevision()),
                              status.getLastChangedDate(), status.getLastCommitAuthor(), convert(status.getTextStatus()),
                              convert(status.getPropStatus()), convert(status.getRepositoryTextStatus()),
                              convert(status.getRepositoryPropStatus()), status.isLocked(), status.isCopied(), status.isSwitched(),
                              status.isFileExternal(), null, null, null, status.getChangelist(), WorkingCopyFormat.ONE_DOT_SEVEN.getFormat(),
                              status.isConflicted(),
                              new Getter<SVNInfo>() {
                                @Override
                                public SVNInfo get() {
                                  return infoGetter.convert(path);
                                }
                              });
  }

  private static SVNURL createUrl(final String url) throws SVNException {
    if (url == null) return null;
    return SVNURL.parseURIEncoded(url);
  }
  
  public static SVNStatusType convert(Status.Kind kind) {
    if (kind == null) return null;
    if (Status.Kind.added.equals(kind)) {
      return SVNStatusType.STATUS_ADDED;
    } else if (Status.Kind.conflicted.equals(kind)) {
      return SVNStatusType.STATUS_CONFLICTED;
    } else if (Status.Kind.deleted.equals(kind)) {
      return SVNStatusType.STATUS_DELETED;
    } else if (Status.Kind.external.equals(kind)) {
      return SVNStatusType.STATUS_EXTERNAL;
    } else if (Status.Kind.ignored.equals(kind)) {
      // 5
      return SVNStatusType.STATUS_IGNORED;
    } else if (Status.Kind.incomplete.equals(kind)) {
      return SVNStatusType.STATUS_INCOMPLETE;
    } else if (Status.Kind.merged.equals(kind)) {
      return SVNStatusType.STATUS_MERGED;
    } else if (Status.Kind.missing.equals(kind)) {
      return SVNStatusType.STATUS_MISSING;
    } else if (Status.Kind.modified.equals(kind)) {
      return SVNStatusType.STATUS_MODIFIED;
    } else if (Status.Kind.none.equals(kind)) {
      //10
      return SVNStatusType.STATUS_NONE;
    } else if (Status.Kind.normal.equals(kind)) {
      return SVNStatusType.STATUS_NORMAL;
    } else if (Status.Kind.obstructed.equals(kind)) {
      return SVNStatusType.STATUS_OBSTRUCTED;
    } else if (Status.Kind.replaced.equals(kind)) {
      return SVNStatusType.STATUS_REPLACED;
    } else if (Status.Kind.unversioned.equals(kind)) {
      return SVNStatusType.STATUS_UNVERSIONED;
    }
    return SVNStatusType.STATUS_NONE;
  }
}
