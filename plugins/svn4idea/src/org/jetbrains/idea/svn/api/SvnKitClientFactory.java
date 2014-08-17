package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.SvnKitAddClient;
import org.jetbrains.idea.svn.annotate.SvnKitAnnotateClient;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.browse.SvnKitBrowseClient;
import org.jetbrains.idea.svn.change.SvnKitChangeListClient;
import org.jetbrains.idea.svn.checkin.SvnKitCheckinClient;
import org.jetbrains.idea.svn.checkin.SvnKitImportClient;
import org.jetbrains.idea.svn.checkout.SvnKitCheckoutClient;
import org.jetbrains.idea.svn.checkout.SvnKitExportClient;
import org.jetbrains.idea.svn.cleanup.SvnKitCleanupClient;
import org.jetbrains.idea.svn.conflict.SvnKitConflictClient;
import org.jetbrains.idea.svn.content.SvnKitContentClient;
import org.jetbrains.idea.svn.copy.SvnKitCopyMoveClient;
import org.jetbrains.idea.svn.delete.SvnKitDeleteClient;
import org.jetbrains.idea.svn.diff.SvnKitDiffClient;
import org.jetbrains.idea.svn.history.SvnKitHistoryClient;
import org.jetbrains.idea.svn.integrate.SvnKitMergeClient;
import org.jetbrains.idea.svn.lock.SvnKitLockClient;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.SvnKitStatusClient;
import org.jetbrains.idea.svn.info.SvnKitInfoClient;
import org.jetbrains.idea.svn.properties.SvnKitPropertyClient;
import org.jetbrains.idea.svn.revert.SvnKitRevertClient;
import org.jetbrains.idea.svn.update.SvnKitRelocateClient;
import org.jetbrains.idea.svn.update.SvnKitUpdateClient;
import org.jetbrains.idea.svn.update.UpdateClient;
import org.jetbrains.idea.svn.upgrade.SvnKitUpgradeClient;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitClientFactory extends ClientFactory {

  public SvnKitClientFactory(@NotNull SvnVcs vcs) {
    super(vcs);
  }

  @Override
  protected void setup() {
    addClient = new SvnKitAddClient();
    annotateClient = new SvnKitAnnotateClient();
    contentClient = new SvnKitContentClient();
    historyClient = new SvnKitHistoryClient();
    revertClient = new SvnKitRevertClient();
    deleteClient = new SvnKitDeleteClient();
    copyMoveClient = new SvnKitCopyMoveClient();
    conflictClient = new SvnKitConflictClient();
    propertyClient = new SvnKitPropertyClient();
    mergeClient = new SvnKitMergeClient();
    changeListClient = new SvnKitChangeListClient();
    checkoutClient = new SvnKitCheckoutClient();
    myLockClient = new SvnKitLockClient();
    myCleanupClient = new SvnKitCleanupClient();
    myRelocateClient = new SvnKitRelocateClient();
    myVersionClient = new SvnKitVersionClient();
    myImportClient = new SvnKitImportClient();
    myExportClient = new SvnKitExportClient();
    myUpgradeClient = new SvnKitUpgradeClient();
    myBrowseClient = new SvnKitBrowseClient();
    myDiffClient = new SvnKitDiffClient();
    myCheckinClient = new SvnKitCheckinClient();
    statusClient = new SvnKitStatusClient();
    infoClient = new SvnKitInfoClient();
    myRepositoryFeaturesClient = new SvnKitRepositoryFeaturesClient();

    put(BrowseClient.class, SvnKitBrowseClient.class);
  }

  @NotNull
  @Override
  public StatusClient createStatusClient(@Nullable ISVNStatusFileProvider provider, @NotNull ProgressTracker handler) {
    return prepare(new SvnKitStatusClient(provider, handler));
  }

  @NotNull
  @Override
  public UpdateClient createUpdateClient() {
    return prepare(new SvnKitUpdateClient());
  }
}
