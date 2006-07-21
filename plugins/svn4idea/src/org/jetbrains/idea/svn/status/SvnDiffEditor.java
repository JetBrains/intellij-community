/*
 * Created by IntelliJ IDEA.
 * User: Alexander.Kitaev
 * Date: 20.07.2006
 * Time: 19:09:29
 */
package org.jetbrains.idea.svn.status;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.util.containers.HashMap;

public class SvnDiffEditor implements ISVNEditor {

  private SVNRepository mySource;
  private SVNRepository myTarget;

  private Map<String, Change> myChanges;

  public SvnDiffEditor(SVNRepository source, SVNRepository target) {
    mySource = source;
    myTarget = target;
    myChanges = new HashMap<String, Change>();
  }

  public Map<String, Change> getChangesMap() {
    return myChanges;
  }

  public void targetRevision(long revision) throws SVNException {
  }
  public void openRoot(long revision) throws SVNException {
  }

  public void deleteEntry(String path, long revision) throws SVNException {
    // deleted - null for target, existing for source.
    Change change = new Change(new DiffContentRevision(path, mySource, false),
            new DiffContentRevision(path, myTarget, true), FileStatus.DELETED);
    myChanges.put(path, change);
  }

  public void absentDir(String path) throws SVNException {
  }
  public void absentFile(String path) throws SVNException {
  }
  public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    FileStatus status = FileStatus.ADDED;
    if (myChanges.containsKey(path) && myChanges.get(path).getFileStatus() == FileStatus.DELETED) {
      // replaced file
      myChanges.remove(path);
      status = FileStatus.MODIFIED;
    }
    Change change = new Change(new DiffContentRevision(path, mySource, false),
            new DiffContentRevision(path, myTarget, false), status);
    myChanges.put(path, change);
  }
  public void openDir(String path, long revision) throws SVNException {
  }
  public void changeDirProperty(String name, String value) throws SVNException {
  }
  public void closeDir() throws SVNException {
  }

  public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    FileStatus status = FileStatus.ADDED;
    if (myChanges.containsKey(path) && myChanges.get(path).getFileStatus() == FileStatus.DELETED) {
      // replaced file
      myChanges.remove(path);
      status = FileStatus.MODIFIED;
    }
    Change change = new Change(new DiffContentRevision(path, mySource, false),
            new DiffContentRevision(path, myTarget, false), status);
    myChanges.put(path, change);
  }

  public void openFile(String path, long revision) throws SVNException {
    Change change = new Change(new DiffContentRevision(path, mySource, false),
            new DiffContentRevision(path, myTarget, false), FileStatus.MODIFIED);
    myChanges.put(path, change);
  }

  public void changeFileProperty(String path, String name, String value) throws SVNException {
  }
  public void closeFile(String path, String textChecksum) throws SVNException {
  }

  public void abortEdit() throws SVNException {
  }

  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
  }

  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    return null;
  }

  public void textDeltaEnd(String path) throws SVNException {
  }

  public SVNCommitInfo closeEdit() throws SVNException {
    return null;
  }

  private static class DiffContentRevision implements ContentRevision {

    private String myPath;
    private SVNRepository myRepository;
    private boolean myIsEmpty;
    private String myContents;
    private FilePath myFilePath;

    public DiffContentRevision(String path, SVNRepository repos, boolean empty) {
      myPath = path;
      myIsEmpty = empty;
      myRepository = repos;
      myFilePath = VcsUtil.getFilePath(myPath);
    }

    @Nullable
    public String getContent() {
      if (myIsEmpty) {
        return "";
      }
      if (myContents == null) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
        try {
          myRepository.getFile(myPath, -1, null, bos);
        } catch (SVNException e) {
          //
          e.printStackTrace();
          return null;
        }
        myContents = new String(bos.toByteArray());
      }
      return myContents;
    }

    @NotNull
    public FilePath getFile() {
      return myFilePath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return new VcsRevisionNumber.Long(10);
    }
  }
}