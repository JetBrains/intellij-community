package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.CmdAddClient;
import org.jetbrains.idea.svn.annotate.CmdAnnotateClient;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.browse.CmdBrowseClient;
import org.jetbrains.idea.svn.change.CmdChangeListClient;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.checkin.CmdImportClient;
import org.jetbrains.idea.svn.checkout.CmdCheckoutClient;
import org.jetbrains.idea.svn.checkout.CmdExportClient;
import org.jetbrains.idea.svn.cleanup.CmdCleanupClient;
import org.jetbrains.idea.svn.conflict.CmdConflictClient;
import org.jetbrains.idea.svn.content.CmdContentClient;
import org.jetbrains.idea.svn.copy.CmdCopyMoveClient;
import org.jetbrains.idea.svn.delete.CmdDeleteClient;
import org.jetbrains.idea.svn.diff.CmdDiffClient;
import org.jetbrains.idea.svn.history.CmdHistoryClient;
import org.jetbrains.idea.svn.info.CmdInfoClient;
import org.jetbrains.idea.svn.info.InfoClient;
import org.jetbrains.idea.svn.integrate.CmdMergeClient;
import org.jetbrains.idea.svn.lock.CmdLockClient;
import org.jetbrains.idea.svn.properties.CmdPropertyClient;
import org.jetbrains.idea.svn.revert.CmdRevertClient;
import org.jetbrains.idea.svn.status.CmdStatusClient;
import org.jetbrains.idea.svn.update.CmdRelocateClient;
import org.jetbrains.idea.svn.update.CmdUpdateClient;
import org.jetbrains.idea.svn.update.UpdateClient;
import org.jetbrains.idea.svn.upgrade.CmdUpgradeClient;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdClientFactory extends ClientFactory {

  public CmdClientFactory(@NotNull SvnVcs vcs) {
    super(vcs);
  }

  @Override
  protected void setup() {
    addClient = new CmdAddClient();
    annotateClient = new CmdAnnotateClient();
    contentClient = new CmdContentClient();
    historyClient = new CmdHistoryClient();
    revertClient = new CmdRevertClient();
    deleteClient = new CmdDeleteClient();
    copyMoveClient = new CmdCopyMoveClient();
    conflictClient = new CmdConflictClient();
    propertyClient = new CmdPropertyClient();
    mergeClient = new CmdMergeClient();
    changeListClient = new CmdChangeListClient();
    checkoutClient = new CmdCheckoutClient();
    myLockClient = new CmdLockClient();
    myCleanupClient = new CmdCleanupClient();
    myRelocateClient = new CmdRelocateClient();
    myVersionClient = new CmdVersionClient();
    myImportClient = new CmdImportClient();
    myExportClient = new CmdExportClient();
    myUpgradeClient = new CmdUpgradeClient();
    myBrowseClient = new CmdBrowseClient();
    myDiffClient = new CmdDiffClient();
    myCheckinClient = new CmdCheckinClient();
    statusClient = new CmdStatusClient();
    infoClient = new CmdInfoClient();
    myRepositoryFeaturesClient = new CmdRepositoryFeaturesClient();

    put(BrowseClient.class, CmdBrowseClient.class);
    put(InfoClient.class, CmdInfoClient.class);
  }

  @NotNull
  @Override
  public UpdateClient createUpdateClient() {
    return prepare(new CmdUpdateClient());
  }
}
