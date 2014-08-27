package org.jetbrains.plugins.settingsRepository;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SingleAlarm;
import com.mcdermottroe.apple.OSXKeychain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.settingsRepository.git.GitRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

public final class IcsManager implements ApplicationLoadListener, Disposable {
  static final Logger LOG = Logger.getInstance(IcsManager.class);

  public static final String PLUGIN_NAME = "Settings Repository";

  private final IcsSettings settings = new IcsSettings();

  private RepositoryManager repositoryManager;

  private IcsStatus status;

  private final AtomicNotNullLazyValue<CredentialsStore> credentialsStore = new AtomicNotNullLazyValue<CredentialsStore>() {
    @NotNull
    @Override
    protected CredentialsStore compute() {
      if (SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard) {
        try {
          OSXKeychain.setLibraryPath(getPathToBundledFile("osxkeychain.so"));
          return new OsXCredentialsStore();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      return new FileCredentialsStore(new File(getPluginSystemDir(), ".git_auth"));
    }
  };

  @NotNull
  private static String getPathToBundledFile(@NotNull String filename) {
    final URL url = IcsManager.class.getResource("");
    String folder;
    if ("jar".equals(url.getProtocol())) {
      // running from build
      folder = "/plugins/settings-repository";
    }
    else {
      // running from sources
      folder = "/settings-repository";
    }
    return FileUtil.toSystemDependentName(PathManager.getHomePath() + folder + "/lib/" + filename);
  }

  private final SingleAlarm commitAlarm = new SingleAlarm(new Runnable() {
    @Override
    public void run() {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IcsBundle.message("task.commit.title")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            repositoryManager.commit(indicator);
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      });
    }
  }, settings.commitDelay);

  private volatile boolean autoCommitEnabled = true;
  private volatile boolean writeAndDeleteProhibited;

  public static IcsManager getInstance() {
    return ApplicationLoadListener.EP_NAME.findExtension(IcsManager.class);
  }

  @NotNull
  public static File getPluginSystemDir() {
    String customPath = System.getProperty("ics.settingsRepository");
    if (customPath == null) {
      return new File(PathManager.getSystemPath(), "settingsRepository");
    }
    else {
      return new File(FileUtil.expandUserHome(customPath));
    }
  }

  @NotNull
  public IcsStatus getStatus() {
    return status;
  }

  private void setStatus(@NotNull IcsStatus value) {
    if (status != value) {
      status = value;
      ApplicationManager.getApplication().getMessageBus().syncPublisher(StatusListener.TOPIC).statusChanged(value);
    }
  }

  @TestOnly
  void setRepositoryManager(@NotNull RepositoryManager repositoryManager) {
    this.repositoryManager = repositoryManager;
  }

  private void scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication().isUnitTestMode()) {
      commitAlarm.cancelAndRequest();
    }
  }

  private void registerApplicationLevelProviders(@NotNull Application application) {
    try {
      settings.load();
    }
    catch (Exception e) {
      LOG.error(e);
    }

    connectAndUpdateRepository(application);

    IcsStreamProvider streamProvider = new IcsStreamProvider(null) {
      @NotNull
      @Override
      public Collection<String> listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return repositoryManager.listSubFileNames(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
      }

      @Override
      public void delete(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        if (writeAndDeleteProhibited) {
          throw new IllegalStateException("Delete is prohibited now");
        }

        repositoryManager.delete(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
        scheduleCommit();
      }
    };
    StateStorageManager storageManager = ((ApplicationImpl)application).getStateStore().getStateStorageManager();
    storageManager.setStreamProvider(streamProvider);

    Collection<String> storageFileNames = storageManager.getStorageFileNames();
    if (!storageFileNames.isEmpty()) {
      updateStoragesFromStreamProvider(storageManager, storageFileNames);
      SchemesManagerFactory.getInstance().updateConfigFilesFromStreamProviders();
    }
  }

  private void registerProjectLevelProviders(Project project) {
    StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();

    StateStorage workspaceFileStorage = storageManager.getFileStateStorage(StoragePathMacros.WORKSPACE_FILE);
    LOG.assertTrue(workspaceFileStorage != null);
    ProjectId projectId = workspaceFileStorage.getState(new ProjectId(), "IcsProjectId", ProjectId.class, null);
    if (projectId == null || projectId.uid == null) {
      // not mapped, if user wants, he can map explicitly, we don't suggest
      // we cannot suggest "map to ICS" for any project that user opens,
      // it will be annoying
      return;
    }

    storageManager.setStreamProvider(new IcsStreamProvider(projectId.uid) {
      @Override
      protected boolean isAutoCommit(String fileSpec, RoamingType roamingType) {
        return !StorageUtil.isProjectOrModuleFile(fileSpec);
      }

      @Override
      public boolean isApplicable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        if (StorageUtil.isProjectOrModuleFile(fileSpec)) {
          // applicable only if file was committed to Settings Server explicitly
          return repositoryManager.has(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
        }

        return settings.shareProjectWorkspace || !fileSpec.equals(StoragePathMacros.WORKSPACE_FILE);
      }
    });

    updateStoragesFromStreamProvider(storageManager, storageManager.getStorageFileNames());
  }

  @SuppressWarnings("SSBasedInspection")
  private void connectAndUpdateRepository(@NotNull Application application) {
    try {
      if (!application.isUnitTestMode()) {
        repositoryManager = new GitRepositoryManager(credentialsStore);
        setStatus(IcsStatus.OPENED);
        if (settings.updateOnStart && repositoryManager.hasUpstream()) {
          // todo progress
          repositoryManager.updateRepository(new EmptyProgressIndicator());
        }
      }
    }
    catch (Exception e) {
      try {
        LOG.error(e);
      }
      finally {
        setStatus(getStatus() == IcsStatus.OPENED ? IcsStatus.UPDATE_FAILED : IcsStatus.OPEN_FAILED);
      }
    }
  }

  @NotNull
  public IcsSettings getSettings() {
    return settings;
  }

  @NotNull
  public RepositoryManager getRepositoryManager() {
    return repositoryManager;
  }

  private static void updateStoragesFromStreamProvider(@NotNull StateStorageManager appStorageManager, @NotNull Collection<String> storageFileNames) {
    for (String storageFileName : storageFileNames) {
      StateStorage stateStorage = appStorageManager.getFileStateStorage(storageFileName);
      if (stateStorage instanceof FileBasedStorage) {
        try {
          FileBasedStorage fileBasedStorage = (FileBasedStorage)stateStorage;
          fileBasedStorage.resetProviderCache();
          fileBasedStorage.updateFileExternallyFromStreamProviders();
        }
        catch (Throwable e) {
          LOG.debug(e);
        }
      }
    }
  }

  @Override
  public void dispose() {
  }

  public void sync(@NotNull final SyncType syncType, @Nullable Project project) throws Exception {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Ref<Exception> exceptionRef = new Ref<Exception>();

    cancelAndDisableAutoCommit();
    try {
      ApplicationManager.getApplication().saveSettings();
      writeAndDeleteProhibited = true;
      ProgressManager.getInstance().run(new Task.Modal(project, IcsBundle.message("task.sync.title"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);

          try {
            repositoryManager.commit(indicator);
            if (syncType == SyncType.MERGE) {
              repositoryManager.pull(indicator);
              repositoryManager.push(indicator);
            }
            else {
              throw new UnsupportedOperationException(syncType.toString());
            }
          }
          catch (Exception e) {
            //noinspection InstanceofCatchParameter
            if (!(e instanceof AuthenticationException)) {
              LOG.error(e);
            }
            exceptionRef.set(e);
          }
        }
      });
    }
    finally {
      autoCommitEnabled = true;
      writeAndDeleteProhibited = false;
    }

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }
  }

  private void cancelAndDisableAutoCommit() {
    autoCommitEnabled = false;
    commitAlarm.cancel();
  }

  public void runInAutoCommitDisabledMode(@NotNull Runnable runnable) {
    cancelAndDisableAutoCommit();
    try {
      runnable.run();
    }
    finally {
      autoCommitEnabled = true;
    }
  }

  private class IcsStreamProvider extends StreamProvider {
    protected final String projectId;

    public IcsStreamProvider(@Nullable String projectId) {
      this.projectId = projectId;
    }

    @Override
    public final boolean saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType, boolean async) {
      if (writeAndDeleteProhibited) {
        throw new IllegalStateException("Save is prohibited now");
      }

      repositoryManager.write(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId), content, size, async);
      if (isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit();
      }
      return false;
    }

    protected boolean isAutoCommit(String fileSpec, RoamingType roamingType) {
      return true;
    }

    @Override
    @Nullable
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      return repositoryManager.read(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
    }

    @Override
    public boolean isEnabled() {
      return status == IcsStatus.OPENED;
    }

    @Override
    public void delete(@NotNull final String fileSpec, @NotNull final RoamingType roamingType) {
    }
  }

  @Override
  public void beforeApplicationLoaded(Application application) {
    registerApplicationLevelProviders(application);

    application.getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC,
                                                    new ProjectLifecycleListener.Adapter() {
                                                      @Override
                                                      public void beforeProjectLoaded(@NotNull Project project) {
                                                        if (!project.isDefault()) {
                                                          getInstance().registerProjectLevelProviders(project);
                                                        }
                                                      }
                                                    });
  }
}
