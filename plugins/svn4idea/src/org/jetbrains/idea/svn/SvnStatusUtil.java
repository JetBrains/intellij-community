package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.util.Arrays;
import java.util.List;

public class SvnStatusUtil {
  private final static List<SVNStatusType> ourUnderAndLive = Arrays.asList(SVNStatusType.STATUS_ADDED, SVNStatusType.STATUS_CONFLICTED,
    SVNStatusType.STATUS_INCOMPLETE, SVNStatusType.MERGED, SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_EXTERNAL,
    SVNStatusType.STATUS_NORMAL, SVNStatusType.STATUS_REPLACED);

  private final static List<SVNStatusType> ourCanBeAdded = Arrays.asList(SVNStatusType.STATUS_IGNORED, SVNStatusType.STATUS_NONE,
                                                                         SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.UNKNOWN);

  private SvnStatusUtil() {
  }

  public static boolean notUnderControl(final SVNStatus status) {
    return (status == null) || (ourCanBeAdded.contains(status.getContentsStatus()));
  }

  public static boolean isValidUnderControlParent(final SVNStatus status) {
    if (status == null) {
      return false;
    }
    if (status.isSwitched() || status.isCopied()) {
      return true;
    }
    return ourUnderAndLive.contains(status.getContentsStatus());
  }

  public static boolean fileCanBeAdded(final Project project, final VirtualFile file) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final SVNStatus status = vcs.getStatusWithCaching(file);
    if (! notUnderControl(status)) {
      return false;
    }
    final VirtualFile parent = file.getParent();
    if ((parent == null) || (! isValidUnderControlParent(vcs.getStatusWithCaching(parent)))) {
      return false;
    }
    return true;
  }
}
