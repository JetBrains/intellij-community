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

import com.intellij.openapi.diagnostic.Logger;
import org.apache.subversion.javahl.ConflictDescriptor;
import org.apache.subversion.javahl.types.ConflictVersion;
import org.apache.subversion.javahl.types.Info;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import java.io.File;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 7:21 PM
 */
public class InfoConvertor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.portable.InfoConvertor");

  public static Info convert(SVNInfo info) {
    throw new UnsupportedOperationException();
  }

  public static SVNInfo convert(Info info) throws SVNException {
    // !! prop time is NOT filled
    final Set<ConflictDescriptor> conflicts = info.getConflicts();
    String oldConflict = null;
    String newConflict = null;
    String wrkConflict = null;
    String propConflict = null;
    SVNTreeConflictDescription description = null;
    if (conflicts != null && ! conflicts.isEmpty()) {
      for (ConflictDescriptor conflict : conflicts) {
        if (ConflictDescriptor.Kind.property.equals(conflict.getKind())) {
          propConflict = conflict.getPropertyName();
        } else if (ConflictDescriptor.Kind.tree.equals(conflict.getKind())) {
          description = createTreeConflict(conflict);
        } else {
          oldConflict = conflict.getBasePath();
          newConflict = conflict.getTheirPath();
          wrkConflict = conflict.getMyPath();
        }
      }
    }

    return new IdeaSVNInfo(new File(info.getPath()), url(info.getUrl()), url(info.getReposRootUrl()),
                       info.getRev(), NodeKindConvertor.convert(info.getKind()), info.getReposUUID(), info.getLastChangedRev(),
                       info.getLastChangedDate(), info.getLastChangedAuthor(), convertSchedule(info.getSchedule()),
                       url(info.getCopyFromUrl()), info.getCopyFromRev(), info.getTextTime(), null,
                       checksum(info), oldConflict, newConflict, wrkConflict, propConflict, null,
                       DepthConvertor.convert(info.getDepth()), info.getChangelistName(), info.getWorkingSize(), description);
  }

  @Nullable
  private static SVNURL url(String url) throws SVNException {
    return url == null ? null : SVNURL.parseURIEncoded(url);
  }

  @Nullable
  private static String checksum(Info info) {
    return info.getChecksum() == null ? null : new String(info.getChecksum().getDigest());
  }

  private static SVNTreeConflictDescription createTreeConflict(final ConflictDescriptor conflict) throws SVNException {
    return new SVNTreeConflictDescription(new File(conflict.getPath()), NodeKindConvertor.convert(conflict.getNodeKind()),
                                          ConflictActionConvertor.create(conflict), ConflictReasonConvertor.convert(conflict.getReason()),
                                          OperationConvertor.convert(conflict.getOperation()),
                                          createConflictVersion(conflict.getSrcLeftVersion()),
                                          createConflictVersion(conflict.getSrcRightVersion()));
  }

  @Nullable
  private static SVNConflictVersion createConflictVersion(ConflictVersion version) throws SVNException {
    return version == null ? null : new SVNConflictVersion(url(version.getReposURL()), version.getPathInRepos(), version.getPegRevision(),
                                  NodeKindConvertor.convert(version.getNodeKind()));
  }

  public static String convertSchedule(Info.ScheduleKind schedule) {
    if (Info.ScheduleKind.normal.equals(schedule)) {
      return "normal";
    } else if (Info.ScheduleKind.add.equals(schedule)) {
      return SVNProperty.SCHEDULE_ADD;
    } else if (Info.ScheduleKind.delete.equals(schedule)) {
      return SVNProperty.SCHEDULE_DELETE;
    } else if (Info.ScheduleKind.replace.equals(schedule)) {
      return SVNProperty.SCHEDULE_REPLACE;
    }
    return "normal";
  }
}
