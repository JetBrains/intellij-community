package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;

public class ExternallyRenamedChange extends Change {
  private String myRenamedTargetName;

  public ExternallyRenamedChange(final ContentRevision beforeRevision, final ContentRevision afterRevision) {
    super(beforeRevision, afterRevision);
  }

  public ExternallyRenamedChange(final ContentRevision beforeRevision, final ContentRevision afterRevision, final FileStatus fileStatus) {
    super(beforeRevision, afterRevision, fileStatus);
  }

  public void setRenamedOrMovedTarget(final FilePath target) {
    myMoved = myRenamed = false;
    
    if ((getBeforeRevision() != null) && (getAfterRevision() != null)) {
      // not external rename or move
      return;
    }
    final FilePath localPath = ChangesUtil.getFilePath(this);
    if (localPath.getIOFile().getAbsolutePath().equals(target.getIOFile().getAbsolutePath())) {
      // not rename or move
      return;
    }

    if (Comparing.equal(target.getParentPath(), localPath.getParentPath())) {
      myRenamed = true;
    } else {
      myMoved = true;
    }

    myRenamedTargetName = target.getName();
    myRenameOrMoveCached = true;
  }

  public String getRenamedText() {
    return VcsBundle.message((getBeforeRevision() != null) ? "change.file.renamed.to.text" : "change.file.renamed.from.text", myRenamedTargetName);
  }

  public String getMovedText(final Project project) {
    return VcsBundle.message((getBeforeRevision() != null) ? "change.file.moved.to.text" : "change.file.moved.from.text", myRenamedTargetName);
  }
}
