package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.update.FileGroup;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class IntegrateEventHandler extends UpdateEventHandler {
  public IntegrateEventHandler(final SvnVcs vcs, final ProgressIndicator progressIndicator) {
    super(vcs, progressIndicator);
  }

  protected boolean handleInDescendants(final SVNEvent event) {
    if ((event.getAction() == SVNEventAction.UPDATE_UPDATE) && (event.getContentsStatus() == SVNStatusType.UNCHANGED) &&
          (event.getPropertiesStatus() == SVNStatusType.UNKNOWN)) {
        myText2 = SvnBundle.message("progres.text2.updated", event.getFile().getName());
      return true;
    } else if (event.getAction() == SVNEventAction.DELETE) {
      addFileToGroup(FileGroup.REMOVED_FROM_REPOSITORY_ID, event);
      myText2 = SvnBundle.message("progress.text2.deleted", event.getFile().getName());
      return true;
    }
    return false;
  }
}
