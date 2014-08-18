/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class SvnUpdateEnvironment extends AbstractSvnUpdateIntegrateEnvironment {

  public SvnUpdateEnvironment(SvnVcs vcs) {
    super(vcs);
  }

  protected AbstractUpdateIntegrateCrawler createCrawler(final UpdateEventHandler eventHandler,
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
    public UpdateCrawler(SvnVcs vcs, UpdateEventHandler handler, boolean totalUpdate,
                         Collection<VcsException> exceptions, UpdatedFiles postUpdateFiles) {
      super(totalUpdate, postUpdateFiles, exceptions, handler, vcs);
    }

    protected void showProgressMessage(final ProgressIndicator progress, final File root) {
      progress.setText(SvnBundle.message("progress.text.updating", root.getAbsolutePath()));
    }

    protected long doUpdate(final File root) throws SvnBindException {
      final long rev;

      final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
      final UpdateRootInfo rootInfo = configuration.getUpdateRootInfo(root, myVcs);

      final SVNURL sourceUrl = getSourceUrl(myVcs, root);
      final boolean isSwitch = rootInfo != null && rootInfo.getUrl() != null && ! rootInfo.getUrl().equals(sourceUrl);
      final SVNRevision updateTo = rootInfo != null && rootInfo.isUpdateToRevision() ? rootInfo.getRevision() : SVNRevision.HEAD;
      if (isSwitch) {
        final UpdateClient updateClient = createUpdateClient(configuration, root, true, sourceUrl);
        myHandler.addToSwitch(root, sourceUrl);
        rev = updateClient.doSwitch(root, rootInfo.getUrl(), SVNRevision.UNDEFINED, updateTo, configuration.getUpdateDepth(),
                                    configuration.isForceUpdate(), false);
      } else {
        final UpdateClient updateClient = createUpdateClient(configuration, root, false, sourceUrl);
        rev = updateClient.doUpdate(root, updateTo, configuration.getUpdateDepth(), configuration.isForceUpdate(), false);
      }

      return rev;
    }

    private UpdateClient createUpdateClient(SvnConfiguration configuration, File root, boolean isSwitch, SVNURL sourceUrl) {
      final UpdateClient updateClient = myVcs.getFactory(root).createUpdateClient();

      if (! isSwitch) {
        updateClient.setIgnoreExternals(configuration.isIgnoreExternals());
      }
      updateClient.setEventHandler(myHandler);
      updateClient.setUpdateLocksOnDemand(configuration.isUpdateLockOnDemand());

      return updateClient;
    }

    protected boolean isMerge() {
      return false;
    }
  }

  @Nullable
  private static SVNURL getSourceUrl(final SvnVcs vcs, final File root) {
    final Info svnInfo = vcs.getInfo(root);
    return svnInfo != null ? svnInfo.getURL() : null;
  }

  public boolean validateOptions(final Collection<FilePath> roots) {
    // TODO: Check if this logic is useful and needs to be uncommented.
    // TODO: Also Check if setXxx() in UpdateRootInfo are thread safe.
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
          final SVNRevision updateRevision = correctRevision(value);
          return true;
          // should be turned on after bugfix with copy url
          //return checkAncestry(ioFile, url, updateRevision);
        }
      }
    }
    catch (SvnBindException e) {
      Messages.showErrorDialog(myVcs.getProject(), e.getMessage(), SvnBundle.message("switch.target.problem.title"));
      return false;
    }*/

    return true;
  }

  private SVNRevision correctRevision(@NotNull UpdateRootInfo value) throws SvnBindException {
    if (SVNRevision.HEAD.equals(value.getRevision())) {
      // find acual revision to update to (a bug if just say head in switch)
      value.setRevision(SvnUtil.getHeadRevision(myVcs, value.getUrl()));
    }
    return value.getRevision();
  }

  // false - do not do update
  private boolean checkAncestry(final File sourceFile, final SVNURL targetUrl, final SVNRevision targetRevision) throws SvnBindException {
    final Info sourceSvnInfo = myVcs.getInfo(sourceFile);
    final Info targetSvnInfo = myVcs.getInfo(targetUrl, targetRevision);

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
    return (Messages.YES == result);
  }
}
