package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.WindowManagerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SingleAlarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

public class IcsManager implements ApplicationLoadListener, Disposable {
  static final Logger LOG = Logger.getInstance(IcsManager.class);

  private static final String PROJECT_ID_KEY = "IDEA_SERVER_PROJECT_ID";

  public static final String PLUGIN_NAME = "Idea Configuration Server";

  private final IcsSettings settings;

  private RepositoryManager repositoryManager;

  private IdeaConfigurationServerStatus status;

  public IcsManager() {
    settings = new IcsSettings();
  }

  public static IcsManager getInstance() {
    return ApplicationLoadListener.EP_NAME.findExtension(IcsManager.class);
  }

  private static String getProjectId(final Project project) {
    String id = PropertiesComponent.getInstance(project).getValue(PROJECT_ID_KEY);
    if (id == null) {
      id = UUID.randomUUID().toString();
      PropertiesComponent.getInstance(project).setValue(PROJECT_ID_KEY, id);
    }
    return id;
  }

  public static File getPluginSystemDir() {
    return new File(PathManager.getSystemPath(), "ideaConfigurationServer");
  }

  public IdeaConfigurationServerStatus getStatus() {
    return status;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setStatus(@NotNull IdeaConfigurationServerStatus value) {
    if (status != value) {
      status = value;
      ApplicationManager.getApplication().getMessageBus().syncPublisher(StatusListener.TOPIC).statusChanged(value);
    }
  }

  public void registerApplicationLevelProviders(Application application) {
    try {
      settings.load();
    }
    catch (Exception e) {
      LOG.error(e);
    }

    connectAndUpdateRepository();

    ICSStreamProvider streamProvider = new ICSStreamProvider(null) {
      @Override
      public String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return repositoryManager.listSubFileNames(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
      }

      @Override
      public void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        repositoryManager.deleteAsync(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
        commitAlarm.cancelAndRequest();
      }
    };
    StateStorageManager storageManager = ((ApplicationImpl)application).getStateStore().getStateStorageManager();
    storageManager.registerStreamProvider(streamProvider, RoamingType.PER_USER);
    storageManager.registerStreamProvider(streamProvider, RoamingType.PER_PLATFORM);
    storageManager.registerStreamProvider(streamProvider, RoamingType.GLOBAL);
  }

  public void registerProjectLevelProviders(Project project) {
    StateStorageManager manager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    String projectId = getProjectId(project);
    manager.registerStreamProvider(new ICSStreamProvider(projectId), RoamingType.PER_PLATFORM);
    manager.registerStreamProvider(new ICSStreamProvider(projectId) {
      @Override
      public void saveContent(@NotNull String fileSpec, @NotNull InputStream content, long size, @NotNull RoamingType roamingType, boolean async) throws IOException {
        if (isShareable(fileSpec)) {
          super.saveContent(fileSpec, content, size, roamingType, async);
        }
      }

      private boolean isShareable(String fileSpec) {
        return settings.shareProjectWorkspace || !fileSpec.equals(StoragePathMacros.WORKSPACE_FILE);
      }

      @Nullable
      @Override
      public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
        return isShareable(fileSpec) ? super.loadContent(fileSpec, roamingType) : null;
      }
    }, RoamingType.PER_USER);
  }

  public void startPing() {
  }

  public void stopPing() {
  }

  public void connectAndUpdateRepository() {
    try {
      repositoryManager = new GitRepositoryManager();
      setStatus(IdeaConfigurationServerStatus.OPENED);
      if (settings.updateOnStart) {
        repositoryManager.updateRepository();
      }
    }
    catch (IOException e) {
      try {
        LOG.error(e);
      }
      finally {
        setStatus(getStatus() == IdeaConfigurationServerStatus.OPENED ? IdeaConfigurationServerStatus.UPDATE_FAILED : IdeaConfigurationServerStatus.OPEN_FAILED);
      }
    }

    notifyIdeaStorage();
  }

  private static void notifyIdeaStorage() {
    StateStorageManager appStorageManager = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager();
    Collection<String> storageFileNames = appStorageManager.getStorageFileNames();
    if (!storageFileNames.isEmpty()) {
      processStorages(appStorageManager, storageFileNames);

      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();
        processStorages(storageManager, storageManager.getStorageFileNames());
      }

      SchemesManagerFactory.getInstance().updateConfigFilesFromStreamProviders();
    }
  }

  public IcsSettings getSettings() {
    return settings;
  }

  public RepositoryManager getRepositoryManager() {
    return repositoryManager;
  }

  public String getStatusText() {
    switch (status) {
      case OPEN_FAILED:
        return "Open repository failed";
      case UPDATE_FAILED:
        return "Update repository failed";
      default:
        return "Unknown";
    }
  }

  private static void processStorages(final StateStorageManager appStorageManager, final Collection<String> storageFileNames) {
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

  private class ICSStreamProvider implements StreamProvider {
    private final String projectId;

    protected final SingleAlarm commitAlarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().run(new Task.Backgroundable(null, "Pushing to ICS server") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            ActionCallback callback = repositoryManager.commit(indicator);
            while (!callback.isProcessed()) {
              try {
                //noinspection BusyWait
                Thread.sleep(300);
              }
              catch (InterruptedException e) {
                break;
              }
              if (indicator.isCanceled()) {
                String message = "Pushing to ICS server canceled";
                LOG.warn(message);
                callback.reject(message);
                break;
              }
            }
          }
        });
      }
    }, settings.commitDelay);

    public ICSStreamProvider(@Nullable String projectId) {
      this.projectId = projectId;
    }

    @Override
    public void saveContent(@NotNull String fileSpec, @NotNull InputStream content, long size, @NotNull RoamingType roamingType, boolean async) throws IOException {
      repositoryManager.write(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId), content, size, async);
      commitAlarm.cancelAndRequest();
    }

    @Override
    @Nullable
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      return repositoryManager.read(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
    }

    @Override
    public boolean isEnabled() {
      return status == IdeaConfigurationServerStatus.OPENED;
    }

    @Override
    public String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void deleteFile(@NotNull final String fileSpec, @NotNull final RoamingType roamingType) {
    }
  }

  static final class IcsProjectLoadListener implements ApplicationComponent, SettingsSavingComponent {
    @Override
    @NotNull
    public String getComponentName() {
      return "IcsProjectLoadListener";
    }

    @Override
    public void initComponent() {
      WindowManager.getInstance().addListener(new WindowManagerListener() {
        @Override
        public void frameCreated(IdeFrame frame) {
          frame.getStatusBar().addWidget(new IcsStatusBarWidget());
        }

        @Override
        public void beforeFrameReleased(IdeFrame frame) {
        }
      });

      getInstance().startPing();
    }

    @Override
    public void disposeComponent() {
      getInstance().stopPing();
    }

    @Override
    public void save() {
      getInstance().getSettings().save();
    }
  }

  @Override
  public void beforeApplicationLoaded(Application application) {
    if (application.isUnitTestMode()) {
      return;
    }

    registerApplicationLevelProviders(application);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC,
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