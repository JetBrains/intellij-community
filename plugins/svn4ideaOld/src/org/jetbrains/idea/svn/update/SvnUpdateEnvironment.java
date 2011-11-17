/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class SvnUpdateEnvironment extends AbstractSvnUpdateIntegrateEnvironment {

  public SvnUpdateEnvironment(SvnVcs vcs) {
    super(vcs);
  }

  protected AbstractUpdateIntegrateCrawler createCrawler(final ISVNEventHandler eventHandler,
                                        final boolean totalUpdate,
                                        final ArrayList<VcsException> exceptions, final UpdatedFiles updatedFiles) {
    return new UpdateCrawler(myVcs, eventHandler, totalUpdate, exceptions, updatedFiles);
  }

  public Configurable createConfigurable(final Collection<FilePath> collection) {
    if (collection.isEmpty()) return null;
    return new SvnUpdateConfigurable(myVcs.getProject()){

      public String getDisplayName() {
        return SvnBundle.message("update.switch.configurable.name");
      }

      protected AbstractSvnUpdatePanel createPanel() {

        return new SvnUpdatePanel(myVcs, collection);
      }
    };
  }

  protected static class UpdateCrawler extends AbstractUpdateIntegrateCrawler {
    public UpdateCrawler(SvnVcs vcs, ISVNEventHandler handler, boolean totalUpdate,
                         Collection<VcsException> exceptions, UpdatedFiles postUpdateFiles) {
      super(totalUpdate, postUpdateFiles, exceptions, handler, vcs);
    }

    protected void showProgressMessage(final ProgressIndicator progress, final File root) {
      progress.setText(SvnBundle.message("progress.text.updating", root.getAbsolutePath()));
    }

    protected long doUpdate(
      final File root,
      final SVNUpdateClient client) throws
                                    SVNException {
      final long rev;

      final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
      final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(root, myVcs);

      final SVNUpdateClient updateClient = myVcs.createUpdateClient();
      updateClient.setEventHandler(myHandler);
      updateClient.setUpdateLocksOnDemand(configuration.UPDATE_LOCK_ON_DEMAND);
      if (rootInfo != null) {
        final SVNURL url = rootInfo.getUrl();
        if (url != null && url.equals(getSourceUrl(myVcs, root))) {
          if (rootInfo.isUpdateToRevision()) {
            rev = updateClient.doUpdate(root, rootInfo.getRevision(), configuration.UPDATE_DEPTH, configuration.FORCE_UPDATE, false);
          } else {
            rev = updateClient.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_DEPTH, configuration.FORCE_UPDATE, false);
          }

        } else if (url != null) {
          rev = updateClient.doSwitch(root, url, SVNRevision.UNDEFINED, rootInfo.getRevision(), configuration.UPDATE_DEPTH, configuration.FORCE_UPDATE, false);
        } else {
          rev = updateClient.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_DEPTH, configuration.FORCE_UPDATE, false);
        }
      } else {
        rev = updateClient.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_DEPTH, configuration.FORCE_UPDATE, false);
      }

      myPostUpdateFiles.setRevisions(root.getAbsolutePath(), myVcs, new SvnRevisionNumber(SVNRevision.create(rev)));

      return rev;
    }

    protected boolean isMerge() {
      return false;
    }
  }

  @Nullable
  private static SVNURL getSourceUrl(final SvnVcs vcs, final File root) {
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      final SVNInfo svnInfo = wcClient.doInfo(root, SVNRevision.WORKING);
      if (svnInfo != null) {
        return svnInfo.getURL();
      } else {
        return null;
      }
    }
    catch (SVNException e) {
      return null;
    }
  }

  public boolean validateOptions(final Collection<FilePath> roots) {
    /*final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());

    final Map<File,UpdateRootInfo> map = configuration.getUpdateInfosMap();
    try {
      for (FilePath root : roots) {
        final File ioFile = root.getIOFile();
        final UpdateRootInfo value = map.get(ioFile);
        if (value == null) {
          continue;
        }
        final SVNURL url = value.getUrl();
        if (url != null && (! url.equals(getSourceUrl(myVcs, root.getIOFile())))) {
          // switch
          final SVNRevision updateRevision = correctRevision(value, url, value.getRevision());
          return true;
          // should be turned on after bugfix with copy url
          //return checkAncestry(ioFile, url, updateRevision);
        }
      }
    }
    catch (SVNException e) {
      Messages.showErrorDialog(myVcs.getProject(), e.getMessage(), SvnBundle.message("switch.target.problem.title"));
      return false;
    }*/

    return true;
  }

  private SVNRevision correctRevision(final UpdateRootInfo value, final SVNURL url, final SVNRevision updateRevision) throws SVNException {
    if (SVNRevision.HEAD.equals(value.getRevision())) {
      // find acual revision to update to (a bug if just say head in switch)
      SVNRepository repository = null;
      try {
        repository = myVcs.createRepository(url);
        final long longRevision = repository.getLatestRevision();
        final SVNRevision newRevision = SVNRevision.create(longRevision);
        value.setRevision(newRevision);
        return newRevision;
      } finally {
        if (repository != null) {
          repository.closeSession();
        }
      }
    }
    return updateRevision;
  }

  // false - do not do update
  private boolean checkAncestry(final File sourceFile, final SVNURL targetUrl, final SVNRevision targetRevision) throws SVNException {
    final SVNWCClient client = myVcs.createWCClient();
    final SVNInfo sourceSvnInfo = client.doInfo(sourceFile, SVNRevision.WORKING);
    final SVNInfo targetSvnInfo = client.doInfo(targetUrl, SVNRevision.UNDEFINED, targetRevision);

    if (sourceSvnInfo == null || targetSvnInfo == null) {
      // cannot check
      return true;
    }

    final SVNURL copyFromTarget = targetSvnInfo.getCopyFromURL();
    final SVNURL copyFromSource = sourceSvnInfo.getCopyFromURL();

    if ((copyFromSource != null) || (copyFromTarget != null)) {
      if (sourceSvnInfo.getURL().equals(copyFromTarget) || targetUrl.equals(copyFromSource)) {
        return true;
      }
    }

    final int result = Messages.showYesNoDialog(myVcs.getProject(), SvnBundle.message("switch.target.not.copy.current"),
                                                SvnBundle.message("switch.target.problem.title"), Messages.getWarningIcon());
    return (DialogWrapper.OK_EXIT_CODE == result);
  }
}
