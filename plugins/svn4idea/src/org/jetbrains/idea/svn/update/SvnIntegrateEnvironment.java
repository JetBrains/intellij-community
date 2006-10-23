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
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class SvnIntegrateEnvironment extends AbstractSvnUpdateIntegrateEnvironment {
  public SvnIntegrateEnvironment(final SvnVcs vcs) {
    super(vcs);
  }

  protected AbstractUpdateIntegrateCrawler createCrawler(ISVNEventHandler eventHandler,
                                                         boolean totalUpdate,
                                                         ArrayList<VcsException> exceptions,
                                                         UpdatedFiles updatedFiles) {
    return new IntegrateCrawler(myVcs, eventHandler, totalUpdate, exceptions, updatedFiles);
  }

  public Configurable createConfigurable(final Collection<FilePath> collection) {
    return new SvnUpdateConfigurable(myVcs.getProject()){
      protected AbstractSvnUpdatePanel createPanel() {
        return new SvnIntegratePanel(myVcs, collection);
      }

      public String getDisplayName() {
        return SvnBundle.message("integrate.display.name");
      }
    };
  }

  private class IntegrateCrawler extends AbstractUpdateIntegrateCrawler {

    public IntegrateCrawler(SvnVcs vcs,
                            ISVNEventHandler handler,
                            boolean totalUpdate,
                            Collection<VcsException> exceptions,
                            UpdatedFiles postUpdateFiles) {
      super(totalUpdate,
            postUpdateFiles,
            exceptions,
            handler, vcs);
    }

    protected void showProgressMessage(final ProgressIndicator progress, final File root) {
      if (SvnConfiguration.getInstance(myVcs.getProject()).MERGE_DRY_RUN) {
        progress.setText(SvnBundle.message("progress.text.merging.dry.run.changes", root.getAbsolutePath()));
      }
      else {
        progress.setText(SvnBundle.message("progress.text.merging.changes", root.getAbsolutePath()));
      }
    }

    protected long doUpdate(
      final File root,
      final SVNUpdateClient client) throws
                                                                                                        SVNException {
      final SvnConfiguration svnConfig = SvnConfiguration.getInstance(myVcs.getProject());

      MergeRootInfo info = svnConfig.getMergeRootInfo(root, myVcs);

      SVNDiffClient diffClient = myVcs.createDiffClient();
      diffClient.setEventHandler(myHandler);
      diffClient.doMerge(info.getUrl1(), info.getRevision1(),
                         info.getUrl2(), info.getRevision2(), root,
                         svnConfig.UPDATE_RECURSIVELY, true, false, svnConfig.MERGE_DRY_RUN);

      SvnConfiguration.getInstance(myVcs.getProject()).LAST_MERGED_REVISION = getLastMergedRevision(info.getRevision2(), info.getUrl2());
      return info.getResultRevision();
    }

    protected boolean isMerge() {
      return true;
    }
  }

  private String getLastMergedRevision(final SVNRevision rev2, final SVNURL svnURL2) {
    if (!rev2.isValid() || rev2.isLocal()) {
      return null;
    }
    else {
      final long number = rev2.getNumber();
      if (number > 0) {
        return String.valueOf(number);
      }
      else {

        try {
          SVNRepository repos = myVcs.createRepository(svnURL2.toString());
          return String.valueOf(repos.getLatestRevision());
        }
        catch (SVNException e) {
          return null;
        }
      }
    }
  }


}
