package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.XmlElementStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemesManagerFactory;
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

  private static final String WORKSPACE_VERSION_FILE = StoragePathMacros.WORKSPACE_FILE + XmlElementStorage.VERSION_FILE_SUFFIX;
  // todo must be configurable - should be in Storage annotation
  private static final String STAT_FILE = StoragePathMacros.APP_CONFIG + "/statistics.application.usages.xml";
  private static final String STAT_VERSION_FILE = STAT_FILE + XmlElementStorage.VERSION_FILE_SUFFIX;

  public static final String PLUGIN_NAME = "Idea Configuration Server";

  private final IcsSettings settings = new IcsSettings();

  private RepositoryManager repositoryManager;

  private IcsStatus status;

  protected final SingleAlarm commitAlarm = new SingleAlarm(new Runnable() {
    @Override
    public void run() {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IcsBundle.message("task.push.title")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          awaitCallback(indicator, repositoryManager.commit(), getTitle());
        }
      });
    }
  }, settings.commitDelay);

  private static void awaitCallback(@NotNull ProgressIndicator indicator, @NotNull ActionCallback callback, @NotNull String title) {
    while (!callback.isProcessed()) {
      try {
        //noinspection BusyWait
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        break;
      }
      if (indicator.isCanceled()) {
        String message = title + " canceled";
        LOG.warn(message);
        callback.reject(message);
        break;
      }
    }
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

  public IcsStatus getStatus() {
    return status;
  }

  public void setStatus(@NotNull IcsStatus value) {
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

    IcsStreamProvider streamProvider = new IcsStreamProvider(null) {
      @NotNull
      @Override
      public Collection<String> listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return repositoryManager.listSubFileNames(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
      }

      @Override
      public void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        if (!isShareable(fileSpec, roamingType)) {
          return;
        }

        repositoryManager.deleteAsync(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
        commitAlarm.cancelAndRequest();
      }

      @Override
      protected boolean isShareable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return !fileSpec.equals(STAT_FILE) && !fileSpec.equals(STAT_VERSION_FILE);
      }
    };
    ((ApplicationImpl)application).getStateStore().getStateStorageManager().setStreamProvider(streamProvider);
  }

  public void registerProjectLevelProviders(Project project) {
    StateStorageManager manager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    String projectId = getProjectId(project);
    manager.setStreamProvider(new IcsStreamProvider(projectId) {
      @Override
      protected boolean isShareable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        if (roamingType != RoamingType.PER_USER) {
          return true;
        }

        if (StorageUtil.isProjectOrModuleFile(fileSpec)) {
          return false;
        }

        return settings.shareProjectWorkspace || (!fileSpec.equals(StoragePathMacros.WORKSPACE_FILE) && !fileSpec.equals(WORKSPACE_VERSION_FILE));
      }
    });
  }

  public void connectAndUpdateRepository() {
    try {
      repositoryManager = new GitRepositoryManager();
      setStatus(IcsStatus.OPENED);
      if (settings.updateOnStart) {
        repositoryManager.updateRepository();
      }
    }
    catch (IOException e) {
      try {
        LOG.error(e);
      }
      finally {
        setStatus(getStatus() == IcsStatus.OPENED ? IcsStatus.UPDATE_FAILED : IcsStatus.OPEN_FAILED);
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

  public ActionCallback sync() {
    commitAlarm.cancel();
    final ActionCallback actionCallback = new ActionCallback(3);
    ProgressManager.getInstance().run(new Task.Modal(null, IcsBundle.message("task.sync.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().saveSettings();
          }
        }, ModalityState.any());
        commitAlarm.cancel();

        repositoryManager.commit().notify(actionCallback);
        repositoryManager.pull(indicator).notify(actionCallback);
        ActionCallback lastActionCallback = repositoryManager.push(indicator).notify(actionCallback);
        awaitCallback(indicator, lastActionCallback, getTitle());
      }
    });
    return actionCallback;
  }

  private class IcsStreamProvider extends com.intellij.openapi.components.impl.stores.StreamProvider {
    private final String projectId;

    public IcsStreamProvider(@Nullable String projectId) {
      this.projectId = projectId;
    }

    protected boolean isShareable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      return true;
    }

    @Override
    public boolean saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType, boolean async) throws IOException {
      if (!isShareable(fileSpec, roamingType)) {
        return false;
      }

      repositoryManager.write(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId), content, size, async);
      commitAlarm.cancelAndRequest();
      return false;
    }

    @Override
    @Nullable
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      if (!isShareable(fileSpec, roamingType)) {
        return null;
      }

      return repositoryManager.read(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
    }

    @Override
    public boolean isEnabled() {
      return status == IcsStatus.OPENED;
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
      // todo find normal way to register our widget
      WindowManager.getInstance().addListener(new WindowManagerListener() {
        @Override
        public void frameCreated(IdeFrame frame) {
          frame.getStatusBar().addWidget(new IcsStatusBarWidget());
        }

        @Override
        public void beforeFrameReleased(IdeFrame frame) {
        }
      });
    }

    @Override
    public void disposeComponent() {
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