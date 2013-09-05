package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

public class IcsManager {
  private static final Logger LOG = Logger.getInstance(IcsManager.class);

  private static final String PROJECT_ID_KEY = "IDEA_SERVER_PROJECT_ID";

  private static final IcsManager instance = new IcsManager();

  public static final File PLUGIN_SYSTEM_DIR = new File(PathManager.getSystemPath(), "ideaConfigurationServer");
  public static final String PLUGIN_NAME = "Idea Configuration Server";

  private final IcsSettings settings;
  private final Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final Runnable updateRequest;

  private RepositoryManager repositoryManager;

  private IdeaConfigurationServerStatus status;

  public IcsManager() {
    settings = new IcsSettings();
    updateRequest = new Runnable() {
      @SuppressWarnings("StatementWithEmptyBody")
      @Override
      public void run() {
        updateAlarm.cancelAllRequests();
        try {
          try {
            if (status == IdeaConfigurationServerStatus.CONNECTION_FAILED && settings.getLogin() != null && settings.getToken() != null) {
              connectAndUpdateStorage();
            }
          }
          catch (Exception e) {
            //ignore
          }
        }
        finally {
          updateAlarm.addRequest(updateRequest, 30 * 1000);
        }
      }
    };
  }

  public static IcsManager getInstance() {
    return instance;
  }

  private static String getProjectId(final Project project) {
    String id = PropertiesComponent.getInstance(project).getValue(PROJECT_ID_KEY);
    if (id == null) {
      id = UUID.randomUUID().toString();
      PropertiesComponent.getInstance(project).setValue(PROJECT_ID_KEY, id);
    }
    return id;
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

    connectAndUpdateStorage();

    ICSStreamProvider streamProvider = new ICSStreamProvider(null) {
      @Override
      public String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return repositoryManager.listSubFileNames(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
      }

      @Override
      public void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        final String path = IcsUrlBuilder.buildPath(fileSpec, roamingType, null);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              repositoryManager.delete(path);
            }
            catch (IOException e) {
              LOG.debug(e);
            }
          }
        });
      }
    };
    StateStorageManager storageManager = ((ApplicationImpl)application).getStateStore().getStateStorageManager();
    storageManager.registerStreamProvider(streamProvider, RoamingType.PER_USER);
    storageManager.registerStreamProvider(streamProvider, RoamingType.PER_PLATFORM);
    storageManager.registerStreamProvider(streamProvider, RoamingType.GLOBAL);
  }

  public void registerProjectLevelProviders(Project project) {
    StateStorageManager manager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    ICSStreamProvider provider = new ICSStreamProvider(getProjectId(project));
    manager.registerStreamProvider(provider, RoamingType.PER_PLATFORM);
    manager.registerStreamProvider(provider, RoamingType.PER_USER);
  }

  public void startPing() {
    updateAlarm.addRequest(updateRequest, 30 * 1000);
  }

  public void stopPing() {
    updateAlarm.cancelAllRequests();
  }

  public void connectAndUpdateStorage() {
    try {
      repositoryManager = new GitRepositoryManager();
      repositoryManager.updateRepo();
      //settings.setStatus(IdeaConfigurationServerStatus.LOGGED_IN);
    }
    catch (IOException e) {
      //settings.setStatus(IdeaConfigurationServerStatus.CONNECTION_FAILED);
      LOG.error(e);
    }

    if (settings.updateOnStart) {
      updateStorage();
    }
  }

  private static void updateStorage() {
    StateStorageManager appStorageManager = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager();
    Collection<String> storageFileNames = appStorageManager.getStorageFileNames();
    if (!storageFileNames.isEmpty()) {
      processStorages(appStorageManager, storageFileNames);

      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project : projects) {
        StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();
        processStorages(storageManager, storageManager.getStorageFileNames());
      }

      SchemesManagerFactory.getInstance().updateConfigFilesFromStreamProviders();
    }
  }

  public IcsSettings getIdeaServerSettings() {
    return settings;
  }

  public String getStatusText() {
    switch (status) {
      case CONNECTION_FAILED:
        return "Connection failed";
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

  private class ICSStreamProvider implements CurrentUserHolder, StreamProvider {
    private final String projectId;

    public ICSStreamProvider(@Nullable String projectId) {
      this.projectId = projectId;
    }

    @Override
    public void saveContent(@NotNull String fileSpec, @NotNull final InputStream content, final long size, @NotNull RoamingType roamingType, boolean async) throws IOException {
      final String path = IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId);
      if (async) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              repositoryManager.write(path, content, size);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        });
      }
      else {
        repositoryManager.write(path, content, size);
      }
    }

    @Override
    @Nullable
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      if (projectId == null || roamingType == RoamingType.PER_PLATFORM) {
        return repositoryManager.read(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
      }
      else if (StoragePathMacros.WORKSPACE_FILE.equals(fileSpec)) {
        return repositoryManager.read(IcsUrlBuilder.buildPath(fileSpec, roamingType, projectId));
      }
      else {
        return null;
      }
    }

    @Override
    public String getCurrentUserName() {
      return settings.getLogin();
    }

    @Override
    public boolean isEnabled() {
      // todo configurable
      return true;
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
      getInstance().startPing();
    }

    @Override
    public void disposeComponent() {
      getInstance().stopPing();
    }

    @Override
    public void save() {
      getInstance().getIdeaServerSettings().save();
    }
  }

  static final class IcsApplicationLoadListener implements ApplicationLoadListener {
    @Override
    public void beforeApplicationLoaded(Application application) {
      if (application.isUnitTestMode()) {
        return;
      }

      getInstance().registerApplicationLevelProviders(application);

      ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
        @Override
        public void beforeProjectLoaded(@NotNull Project project) {
          if (!project.isDefault()) {
            getInstance().registerProjectLevelProviders(project);
          }
        }
      });
    }
  }
}