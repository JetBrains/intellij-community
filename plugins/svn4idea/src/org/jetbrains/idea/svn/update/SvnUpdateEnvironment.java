/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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

      if (rootInfo != null) {
        final SVNURL url = rootInfo.getUrl();
        if (url != null && url.equals(getSourceUrl(root))) {
          if (rootInfo.isUpdateToRevision()) {
            rev = client.doUpdate(root, rootInfo.getRevision(), configuration.UPDATE_RECURSIVELY);
          } else {
            rev = client.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_RECURSIVELY);
          }

        } else if (url != null) {
          rev = client.doSwitch(root, url,
                                rootInfo.getRevision(), configuration.UPDATE_RECURSIVELY);
        } else {
          rev = client.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_RECURSIVELY);
        }
      } else {
        rev = client.doUpdate(root, SVNRevision.HEAD, configuration.UPDATE_RECURSIVELY);
      }

      return rev;
    }

    protected boolean isMerge() {
      return false;
    }

    private SVNURL getSourceUrl(final File root) {
      try {
        SVNWCClient wcClient = myVcs.createWCClient();
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
  }
}