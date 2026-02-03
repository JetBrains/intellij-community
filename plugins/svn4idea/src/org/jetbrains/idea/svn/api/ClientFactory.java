// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.AddClient;
import org.jetbrains.idea.svn.annotate.AnnotateClient;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.change.ChangeListClient;
import org.jetbrains.idea.svn.checkin.CheckinClient;
import org.jetbrains.idea.svn.checkin.ImportClient;
import org.jetbrains.idea.svn.checkout.CheckoutClient;
import org.jetbrains.idea.svn.checkout.ExportClient;
import org.jetbrains.idea.svn.cleanup.CleanupClient;
import org.jetbrains.idea.svn.conflict.ConflictClient;
import org.jetbrains.idea.svn.content.ContentClient;
import org.jetbrains.idea.svn.copy.CopyMoveClient;
import org.jetbrains.idea.svn.delete.DeleteClient;
import org.jetbrains.idea.svn.diff.DiffClient;
import org.jetbrains.idea.svn.history.HistoryClient;
import org.jetbrains.idea.svn.info.InfoClient;
import org.jetbrains.idea.svn.integrate.MergeClient;
import org.jetbrains.idea.svn.lock.LockClient;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.revert.RevertClient;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.update.RelocateClient;
import org.jetbrains.idea.svn.update.UpdateClient;
import org.jetbrains.idea.svn.upgrade.UpgradeClient;

import java.util.HashMap;
import java.util.Map;

public abstract class ClientFactory {

  protected @NotNull SvnVcs myVcs;

  protected AddClient addClient;
  protected AnnotateClient annotateClient;
  protected ContentClient contentClient;
  protected HistoryClient historyClient;
  protected RevertClient revertClient;
  protected DeleteClient deleteClient;
  protected StatusClient statusClient;
  protected InfoClient infoClient;
  protected CopyMoveClient copyMoveClient;
  protected ConflictClient conflictClient;
  protected PropertyClient propertyClient;
  protected MergeClient mergeClient;
  protected ChangeListClient changeListClient;
  protected CheckoutClient checkoutClient;
  protected LockClient myLockClient;
  protected CleanupClient myCleanupClient;
  protected RelocateClient myRelocateClient;
  protected VersionClient myVersionClient;
  protected ImportClient myImportClient;
  protected ExportClient myExportClient;
  protected UpgradeClient myUpgradeClient;
  protected BrowseClient myBrowseClient;
  protected DiffClient myDiffClient;
  protected CheckinClient myCheckinClient;
  protected RepositoryFeaturesClient myRepositoryFeaturesClient;

  private final @NotNull Map<Class, Class> myClientImplementations = new HashMap<>();

  protected ClientFactory(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  protected <T extends SvnClient> void put(@NotNull Class<T> type, @NotNull Class<? extends T> implementation) {
    myClientImplementations.put(type, implementation);
  }

  @SuppressWarnings("unchecked")
  protected @NotNull <T extends SvnClient> Class<? extends T> get(@NotNull Class<T> type) {
    Class<? extends T> implementation = myClientImplementations.get(type);

    if (implementation == null) {
      throw new IllegalArgumentException("No implementation registered for " + type);
    }

    return implementation;
  }

  /**
   * TODO: Provide more robust way for the default settings here - probably some default Command instance could be used.
   */
  public @NotNull <T extends SvnClient> T create(@NotNull Class<T> type, boolean isActive) {
    T client = prepare(ReflectionUtil.newInstance(get(type)));
    client.setIsActive(isActive);

    return client;
  }

  public @NotNull AddClient createAddClient() {
    return prepare(addClient);
  }

  public @NotNull AnnotateClient createAnnotateClient() {
    return prepare(annotateClient);
  }

  public @NotNull ContentClient createContentClient() {
    return prepare(contentClient);
  }

  public @NotNull HistoryClient createHistoryClient() {
    return prepare(historyClient);
  }

  public @NotNull RevertClient createRevertClient() {
    return prepare(revertClient);
  }

  public @NotNull StatusClient createStatusClient() {
    return prepare(statusClient);
  }

  public @NotNull InfoClient createInfoClient() {
    return prepare(infoClient);
  }

  // TODO: Update this in same like other clients - move to corresponding package, rename clients
  // New instances should be always created by this method, as setXxx() methods are currently used in update logic
  public abstract @NotNull UpdateClient createUpdateClient();

  public @NotNull DeleteClient createDeleteClient() {
    return prepare(deleteClient);
  }

  public @NotNull CopyMoveClient createCopyMoveClient() {
    return prepare(copyMoveClient);
  }

  public @NotNull ConflictClient createConflictClient() {
    return prepare(conflictClient);
  }

  public @NotNull PropertyClient createPropertyClient() {
    return prepare(propertyClient);
  }

  public @NotNull MergeClient createMergeClient() {
    return prepare(mergeClient);
  }

  public @NotNull ChangeListClient createChangeListClient() {
    return prepare(changeListClient);
  }

  public @NotNull CheckoutClient createCheckoutClient() {
    return prepare(checkoutClient);
  }

  public @NotNull LockClient createLockClient() {
    return prepare(myLockClient);
  }

  public @NotNull CleanupClient createCleanupClient() {
    return prepare(myCleanupClient);
  }

  public @NotNull RelocateClient createRelocateClient() {
    return prepare(myRelocateClient);
  }

  public @NotNull VersionClient createVersionClient() {
    return prepare(myVersionClient);
  }

  public @NotNull ImportClient createImportClient() {
    return prepare(myImportClient);
  }

  public @NotNull ExportClient createExportClient() {
    return prepare(myExportClient);
  }

  public @NotNull UpgradeClient createUpgradeClient() {
    return prepare(myUpgradeClient);
  }

  public @NotNull BrowseClient createBrowseClient() {
    return prepare(myBrowseClient);
  }

  public @NotNull DiffClient createDiffClient() {
    return prepare(myDiffClient);
  }

  public @NotNull CheckinClient createCheckinClient() {
    return prepare(myCheckinClient);
  }

  public @NotNull RepositoryFeaturesClient createRepositoryFeaturesClient() {
    return prepare(myRepositoryFeaturesClient);
  }

  protected @NotNull <T extends SvnClient> T prepare(@NotNull T client) {
    client.setVcs(myVcs);
    client.setFactory(this);
    client.setIsActive(true);

    return client;
  }
}
