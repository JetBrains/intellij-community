/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 17:20:32
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class SvnChangeList implements CommittedChangeList {
  private final SVNLogEntry myLogEntry;
  private final SVNRepository myRepository;
  private List<Change> myChanges;

  public SvnChangeList(final SVNLogEntry logEntry, SVNRepository repository) {
    myLogEntry = logEntry;
    myRepository = repository;
  }

  public String getCommitterName() {
    return myLogEntry.getAuthor();
  }

  public Date getCommitDate() {
    return myLogEntry.getDate();
  }

  public Collection<Change> getChanges() {
    if (myChanges == null) {
      loadChanges();
    }
    return myChanges;
  }

  private void loadChanges() {
    myChanges = new ArrayList<Change>();
    for(Object o: myLogEntry.getChangedPaths().values()) {
      SVNLogEntryPath entry = (SVNLogEntryPath) o;
      SvnContentRevision beforeRevision = (entry.getType() == 'A')
                                          ? null
                                          : new SvnContentRevision(myRepository, entry, myLogEntry.getRevision()-1);
      SvnContentRevision afterRevision = (entry.getType() == 'D')
                                         ? null
                                         : new SvnContentRevision(myRepository, entry, myLogEntry.getRevision());
      myChanges.add(new Change(beforeRevision, afterRevision));
    }
  }

  public String getName() {
    return myLogEntry.getMessage();
  }

  public String getComment() {
    return myLogEntry.getMessage();
  }
}