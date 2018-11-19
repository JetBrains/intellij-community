// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.integrate.MergeClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class SvnIntegrateEnvironment extends AbstractSvnUpdateIntegrateEnvironment {
  public SvnIntegrateEnvironment(final SvnVcs vcs) {
    super(vcs);
  }

  @Override
  protected AbstractUpdateIntegrateCrawler createCrawler(UpdateEventHandler eventHandler,
                                                         boolean totalUpdate,
                                                         ArrayList<VcsException> exceptions,
                                                         UpdatedFiles updatedFiles) {
    return new IntegrateCrawler(myVcs, eventHandler, totalUpdate, exceptions, updatedFiles);
  }

  @Override
  public Configurable createConfigurable(final Collection<FilePath> collection) {
    if (collection.isEmpty()) return null;
    return new SvnUpdateConfigurable(myVcs.getProject()){
      @Override
      protected AbstractSvnUpdatePanel createPanel() {
        return new SvnIntegratePanel(myVcs, collection);
      }

      @Override
      public String getDisplayName() {
        return SvnBundle.message("integrate.display.name");
      }

      @Override
      public String getHelpTopic() {
        return "reference.dialogs.versionControl.integrate.project.subversion";
      }
    };
  }

  @Override
  protected boolean isDryRun() {
    return myVcs.getSvnConfiguration().isMergeDryRun();
  }

  private static class IntegrateCrawler extends AbstractUpdateIntegrateCrawler {

    IntegrateCrawler(SvnVcs vcs,
                            UpdateEventHandler handler,
                            boolean totalUpdate,
                            Collection<VcsException> exceptions,
                            UpdatedFiles postUpdateFiles) {
      super(totalUpdate,
            postUpdateFiles,
            exceptions,
            handler, vcs);
    }

    @Override
    protected void showProgressMessage(final ProgressIndicator progress, final File root) {
      if (myVcs.getSvnConfiguration().isMergeDryRun()) {
        progress.setText(SvnBundle.message("progress.text.merging.dry.run.changes", root.getAbsolutePath()));
      }
      else {
        progress.setText(SvnBundle.message("progress.text.merging.changes", root.getAbsolutePath()));
      }
    }

    @Override
    protected long doUpdate(final File root) throws VcsException {
      SvnConfiguration svnConfig = myVcs.getSvnConfiguration();
      MergeRootInfo info = svnConfig.getMergeRootInfo(root, myVcs);
      if (info.getUrlString1().equals(info.getUrlString2()) &&
        info.getRevision1().equals(info.getRevision2())) {
        return 0;
      }

      MergeClient client = myVcs.getFactory(root).createMergeClient();
      Target source1 = Target.on(info.getUrl1(), info.getRevision1());
      Target source2 = Target.on(info.getUrl2(), info.getRevision2());

      client.merge(source1, source2, root, svnConfig.getUpdateDepth(), svnConfig.isMergeDiffUseAncestry(), svnConfig.isMergeDryRun(), false, false,
                   svnConfig.getMergeOptions(), myHandler);
      return info.getResultRevision();
    }

    @Override
    protected boolean isMerge() {
      return true;
    }
  }

  @Override
  public boolean validateOptions(final Collection<FilePath> roots) {
    return true;
  }
}
