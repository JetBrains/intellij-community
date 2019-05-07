package org.jetbrains.idea.svn.api;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class ClientFactory {

  @NotNull
  protected SvnVcs myVcs;

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

  @NotNull private final Map<Class, Class> myClientImplementations = ContainerUtil.newHashMap();

  protected ClientFactory(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  protected <T extends SvnClient> void put(@NotNull Class<T> type, @NotNull Class<? extends T> implementation) {
    myClientImplementations.put(type, implementation);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  protected <T extends SvnClient> Class<? extends T> get(@NotNull Class<T> type) {
    Class<? extends T> implementation = myClientImplementations.get(type);

    if (implementation == null) {
      throw new IllegalArgumentException("No implementation registered for " + type);
    }

    return implementation;
  }

  /**
   * TODO: Provide more robust way for the default settings here - probably some default Command instance could be used.
   */
  @NotNull
  public <T extends SvnClient> T create(@NotNull Class<T> type, boolean isActive) {
    T client = prepare(ReflectionUtil.newInstance(get(type)));
    client.setIsActive(isActive);

    return client;
  }

  @NotNull
  public AddClient createAddClient() {
    return prepare(addClient);
  }

  @NotNull
  public AnnotateClient createAnnotateClient() {
    return prepare(annotateClient);
  }

  @NotNull
  public ContentClient createContentClient() {
    return prepare(contentClient);
  }

  @NotNull
  public HistoryClient createHistoryClient() {
    return prepare(historyClient);
  }

  @NotNull
  public RevertClient createRevertClient() {
    return prepare(revertClient);
  }

  @NotNull
  public StatusClient createStatusClient() {
    return prepare(statusClient);
  }

  @NotNull
  public StatusClient createStatusClient(@Nullable MultiMap<FilePath, FilePath> scope, @NotNull ProgressTracker handler) {
    return createStatusClient();
  }

  @NotNull
  public InfoClient createInfoClient() {
    return prepare(infoClient);
  }

  // TODO: Update this in same like other clients - move to corresponding package, rename clients
  // New instances should be always created by this method, as setXxx() methods are currently used in update logic
  @NotNull
  public abstract UpdateClient createUpdateClient();

  @NotNull
  public DeleteClient createDeleteClient() {
    return prepare(deleteClient);
  }

  @NotNull
  public CopyMoveClient createCopyMoveClient() {
    return prepare(copyMoveClient);
  }

  @NotNull
  public ConflictClient createConflictClient() {
    return prepare(conflictClient);
  }

  @NotNull
  public PropertyClient createPropertyClient() {
    return prepare(propertyClient);
  }

  @NotNull
  public MergeClient createMergeClient() {
    return prepare(mergeClient);
  }

  @NotNull
  public ChangeListClient createChangeListClient() {
    return prepare(changeListClient);
  }

  @NotNull
  public CheckoutClient createCheckoutClient() {
    return prepare(checkoutClient);
  }

  @NotNull
  public LockClient createLockClient() {
    return prepare(myLockClient);
  }

  @NotNull
  public CleanupClient createCleanupClient() {
    return prepare(myCleanupClient);
  }

  @NotNull
  public RelocateClient createRelocateClient() {
    return prepare(myRelocateClient);
  }

  @NotNull
  public VersionClient createVersionClient() {
    return prepare(myVersionClient);
  }

  @NotNull
  public ImportClient createImportClient() {
    return prepare(myImportClient);
  }

  @NotNull
  public ExportClient createExportClient() {
    return prepare(myExportClient);
  }

  @NotNull
  public UpgradeClient createUpgradeClient() {
    return prepare(myUpgradeClient);
  }

  @NotNull
  public BrowseClient createBrowseClient() {
    return prepare(myBrowseClient);
  }

  @NotNull
  public DiffClient createDiffClient() {
    return prepare(myDiffClient);
  }

  @NotNull
  public CheckinClient createCheckinClient() {
    return prepare(myCheckinClient);
  }

  @NotNull
  public RepositoryFeaturesClient createRepositoryFeaturesClient() {
    return prepare(myRepositoryFeaturesClient);
  }

  @NotNull
  protected <T extends SvnClient> T prepare(@NotNull T client) {
    client.setVcs(myVcs);
    client.setFactory(this);
    client.setIsActive(true);

    return client;
  }
}
