package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;

public class SvnDiffProvider implements DiffProvider {
  private final SvnVcs myVcs;

  public SvnDiffProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    return new SvnRevisionNumber(SVNRevision.BASE);
  }

  public VcsRevisionNumber getLastRevision(VirtualFile file) {
    return new SvnRevisionNumber(SVNRevision.HEAD);
  }

  public VcsFileContent createFileContent(final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile) {
    final SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();
    return new VcsFileContent() {
      private byte[] myContent;

      public void loadContent() throws VcsException {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        File file = new File(selectedFile.getPath()).getAbsoluteFile();
        ConentLoader loader = new ConentLoader(file, contents, svnRevision);
        if (ApplicationManager.getApplication().isDispatchThread() &&
            !svnRevision.isLocal()) {
          ApplicationManager.getApplication().runProcessWithProgressSynchronously(loader,
                                                                                  "Loading Remote File Content", false, null);
        }
        else {
          loader.run();
        }
        if (loader.getException() != null) {
          throw new VcsException(loader.getException());
        }
        myContent = contents.toByteArray();
      }

      public byte[] getContent() {
        return myContent;
      }
    };
  }

  private class ConentLoader implements Runnable {
    private SVNRevision myRevision;
    private File myFile;
    private OutputStream myDst;
    private SVNException myException;

    public ConentLoader(File file, OutputStream dst, SVNRevision revision) {
      myFile = file;
      myDst = dst;
      myRevision = revision;
    }

    public SVNException getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText("Loading contents of '" + myFile.getName() + "'");
        progress.setText2("Revision " + myRevision);
      }
      try {
        SVNWCClient client = myVcs.createWCClient();
        client.doGetFileContents(myFile, SVNRevision.UNDEFINED, myRevision, true, myDst);
      }
      catch (SVNException e) {
        myException = e;
      }
    }
  }
}
