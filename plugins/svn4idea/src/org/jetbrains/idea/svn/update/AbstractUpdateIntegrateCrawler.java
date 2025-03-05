// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnWCRootCrawler;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;
import java.util.Collection;

public abstract class AbstractUpdateIntegrateCrawler implements SvnWCRootCrawler {
  protected final SvnVcs myVcs;
  protected final UpdateEventHandler myHandler;
  protected final Collection<VcsException> myExceptions;
  protected final UpdatedFiles myPostUpdateFiles;
  protected final boolean myIsTotalUpdate;
  private static final Logger LOG = Logger.getInstance(AbstractUpdateIntegrateCrawler.class);

  protected AbstractUpdateIntegrateCrawler(
    final boolean isTotalUpdate,
    final UpdatedFiles postUpdateFiles,
    final Collection<VcsException> exceptions,
    final UpdateEventHandler handler,
    final SvnVcs vcs) {
    myIsTotalUpdate = isTotalUpdate;
    myPostUpdateFiles = postUpdateFiles;
    myExceptions = exceptions;
    myHandler = handler;
    myVcs = vcs;
  }

  @Override
  public void handleWorkingCopyRoot(File root, ProgressIndicator progress) {
    if (progress != null) {
      showProgressMessage(progress, root);
    }

    myHandler.startUpdate();
    try {
      long rev = doUpdate(root);

      if (rev < 0 && !isMerge()) {
        throw new SvnBindException(SvnBundle.message("exception.text.root.was.not.properly.updated", root));
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      myExceptions.add(e);
    }
    finally {
      myHandler.finishUpdate();
    }
  }

  protected abstract void showProgressMessage(ProgressIndicator progress, File root);

  protected abstract long doUpdate(File root) throws VcsException;

  protected abstract boolean isMerge();
}
