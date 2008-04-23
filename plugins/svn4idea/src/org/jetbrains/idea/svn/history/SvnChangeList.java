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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SvnChangeList implements CommittedChangeList {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history");

  private SvnVcs myVcs;
  private SvnRepositoryLocation myLocation;
  private String myRepositoryRoot;
  private long myRevision;
  private String myAuthor;
  private Date myDate;
  private String myMessage;
  private Set<String> myChangedPaths = new HashSet<String>();
  private Set<String> myAddedPaths = new HashSet<String>();
  private Set<String> myDeletedPaths = new HashSet<String>();
  private List<Change> myChanges;

  private File myWcRoot;
  private SVNURL myBranchUrl;
  private boolean myBranchInfoLoaded;

  public SvnChangeList(SvnVcs vcs, @NotNull final SvnRepositoryLocation location, final SVNLogEntry logEntry, String repositoryRoot) {
    myVcs = vcs;
    myLocation = location;
    myRevision = logEntry.getRevision();
    final String author = logEntry.getAuthor();
    myAuthor = author == null ? "" : author;
    myDate = logEntry.getDate();
    final String message = logEntry.getMessage();
    myMessage = message == null ? "" : message;
    for(Object o: logEntry.getChangedPaths().values()) {
      SVNLogEntryPath entry = (SVNLogEntryPath) o;
      if (entry.getType() == 'A') {
        myAddedPaths.add(entry.getPath());
      }
      else if (entry.getType() == 'D') {
        myDeletedPaths.add(entry.getPath());
      }
      else {
        myChangedPaths.add(entry.getPath());
      }
    }
    myRepositoryRoot = repositoryRoot;
    updateBranchInfo();
  }

  public SvnChangeList(SvnVcs vcs, @NotNull SvnRepositoryLocation location, DataInput stream) throws IOException {
    myVcs = vcs;
    myLocation = location;
    readFromStream(stream);
    updateBranchInfo();
  }

  public String getCommitterName() {
    return myAuthor;
  }

  public Date getCommitDate() {
    return myDate;
  }

  public Collection<Change> getChanges() {
    if (myChanges == null) {
      loadChanges();
    }
    return myChanges;
  }

  private void loadChanges() {
    myChanges = new ArrayList<Change>();
    for(String path: myAddedPaths) {
      myChanges.add(new Change(null, SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, getLocalPath(path), myRevision)));
    }
    for(String path: myDeletedPaths) {
      myChanges.add(new Change(SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, getLocalPath(path), myRevision-1), null));
    }
    for(String path: myChangedPaths) {
      SvnRepositoryContentRevision beforeRevision = SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, getLocalPath(path), myRevision-1);
      SvnRepositoryContentRevision afterRevision = SvnRepositoryContentRevision.create(myVcs, myRepositoryRoot, path, getLocalPath(path), myRevision);
      myChanges.add(new Change(beforeRevision, afterRevision));
    }
  }

  @Nullable
  private FilePath getLocalPath(final String path) {
    if (myLocation.getRootFile() == null) {
      return null;
    }
    String fullPath = myRepositoryRoot + path;
    if (fullPath.startsWith(myLocation.getURL())) {
      String relPath = fullPath.substring(myLocation.getURL().length());
      final String basePath = myLocation.getRootFile().getPresentableUrl();
      File localFile = new File(basePath, relPath);
      return VcsContextFactory.SERVICE.getInstance().createFilePathOn(localFile);
    }
    return null;
  }

  @NotNull
  public String getName() {
    return myMessage;
  }

  public String getComment() {
    return myMessage;
  }

  public long getNumber() {
    return myRevision;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnChangeList that = (SvnChangeList)o;

    if (myRevision != that.myRevision) return false;
    if (myAuthor != null ? !myAuthor.equals(that.myAuthor) : that.myAuthor != null) return false;
    if (myDate != null ? !myDate.equals(that.myDate) : that.myDate != null) return false;
    if (myMessage != null ? !myMessage.equals(that.myMessage) : that.myMessage != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (int)(myRevision ^ (myRevision >>> 32));
    result = 31 * result + (myAuthor != null ? myAuthor.hashCode() : 0);
    result = 31 * result + (myDate != null ? myDate.hashCode() : 0);
    result = 31 * result + (myMessage != null ? myMessage.hashCode() : 0);
    return result;
  }

  public String toString() {
    return myMessage;
  }

  public void writeToStream(final DataOutput stream) throws IOException {
    stream.writeUTF(myRepositoryRoot);
    stream.writeLong(myRevision);
    stream.writeUTF(myAuthor);
    stream.writeLong(myDate.getTime());
    IOUtil.writeUTFTruncated(stream, myMessage);
    writeFiles(stream, myChangedPaths);
    writeFiles(stream, myAddedPaths);
    writeFiles(stream, myDeletedPaths);
  }

  private static void writeFiles(final DataOutput stream, final Set<String> paths) throws IOException {
    stream.writeInt(paths.size());
    for(String s: paths) {
      stream.writeUTF(s);
    }
  }

  private void readFromStream(final DataInput stream) throws IOException {
    myRepositoryRoot = stream.readUTF();
    myRevision = stream.readLong();
    myAuthor = stream.readUTF();
    myDate = new Date(stream.readLong());
    myMessage = stream.readUTF();
    readFiles(stream, myChangedPaths);
    readFiles(stream, myAddedPaths);
    readFiles(stream, myDeletedPaths);
  }

  private static void readFiles(final DataInput stream, final Set<String> paths) throws IOException {
    int count = stream.readInt();
    for(int i=0; i<count; i++) {
      paths.add(stream.readUTF());
    }
  }

  public SVNURL getBranchUrl() {
    if (! myBranchInfoLoaded) {
      updateBranchInfo();
    }
    return myBranchUrl;
  }

  public File getWCRootRoot() {
    if (! myBranchInfoLoaded) {
      updateBranchInfo();
    }
    return myWcRoot;
  }

  private void updateBranchInfo() {
    final Collection<Change> changes = getChanges();
    myBranchInfoLoaded = true;

    FilePath anyFile = null;
    SVNURL anyUrl = null;
    if (! changes.isEmpty()) {
      try {
        final Change firstChange = changes.iterator().next();
        SvnRepositoryContentRevision revision = (SvnRepositoryContentRevision) firstChange.getBeforeRevision();
        revision = (revision == null) ? (SvnRepositoryContentRevision) firstChange.getAfterRevision() : revision;
        anyFile = revision.getFile();
        anyUrl = SVNURL.parseURIDecoded(revision.getFullPath());
      } catch (SVNException e) {
        return;
      }
    }

    if ((anyFile == null) || (anyUrl == null)) {
      return;
    }

    myWcRoot = SvnUtil.getWorkingCopyRoot(anyFile.getIOFile());
    myBranchUrl = getBranchForUrl(anyFile, anyUrl);
  }

  @Nullable
  private SVNURL getBranchForUrl(final FilePath file, final SVNURL url) {
    final Project project = myVcs.getProject();
    final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
    if (vcsRoot == null) {
      return null;
    }
    final SvnBranchConfiguration configuration;
    try {
      configuration = SvnBranchConfigurationManager.getInstance(project).get(vcsRoot);
      return configuration.getWorkingBranch(project, url);
    }
    catch (SVNException e) {
      return null;
    } catch (VcsException e1) {
      return null;
    }
  }
}