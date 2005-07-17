package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 04.07.2005
 * Time: 14:53:47
 * To change this template use File | Settings | File Templates.
 */
public class SvnEditFileProvider implements EditFileProvider {
  private SvnVcs myVCS;

  public SvnEditFileProvider(SvnVcs vcs) {
    myVCS = vcs;
  }

  public void editFiles(VirtualFile[] files) throws VcsException {
    File[] ioFiles = new File[files.length];
    SVNWCClient client;
    try {
      client = myVCS.createWCClient();
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    for (int i = 0; i < files.length; i++) {
      ioFiles[i] = new File(files[i].getPath());
      try {
        SVNPropertyData property = client
          .doGetProperty(ioFiles[i], SVNProperty.NEEDS_LOCK, SVNRevision.WORKING, SVNRevision.WORKING, false);
        if (property == null || property.getValue() == null) {
          throw new VcsException("File '" + ioFiles[i].getName() + "' is readonly, but miss svn:needs-lock property");
        }
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }
    }
    SvnUtil.doLockFiles(myVCS.getProject(), myVCS, ioFiles, null);
  }

  public String getRequestText() {
    return "File(s) you're are going to edit needs to be locked before editing";
  }
}
