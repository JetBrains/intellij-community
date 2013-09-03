package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class IcsManager {
  private static final Logger LOG = Logger.getInstance(IcsManager.class);

  private static final ExecutorService ourThreadExecutorsService = ConcurrencyUtil.newSingleThreadExecutor("IdeaServer executor");

  private static final String PROJECT_ID_KEY = "IDEA_SERVER_PROJECT_ID";

  private static final IcsManager instance = new IcsManager();

  private final IdeaConfigurationServerSettings settings;
  private final Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final Runnable updateRequest;

  private IcsGitConnector serverConnector;

  public IcsManager() {
    settings = new IdeaConfigurationServerSettings();
    updateRequest = new Runnable() {
      @SuppressWarnings("StatementWithEmptyBody")
      @Override
      public void run() {
        updateAlarm.cancelAllRequests();
        try {
          try {
            if (settings.getStatus() == IdeaConfigurationServerStatus.CONNECTION_FAILED && settings.getUserName() != null && settings.getPassword() != null) {
              login();
            }
            else if (settings.getStatus() == IdeaConfigurationServerStatus.LOGGED_IN && settings.getUserName() != null && settings.getPassword() != null) {
              //ping();
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

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(StatusListener.TOPIC, new StatusListener() {
      @Override
      public void statusChanged(final IdeaConfigurationServerStatus status) {
        if (status == IdeaConfigurationServerStatus.LOGGED_IN) {
          serverConnector.updateRepo();
        }
      }
    });
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

  public void registerApplicationLevelProviders(Application application) {
    settings.load();

    if (settings.REMEMBER_SETTINGS || GraphicsEnvironment.isHeadless()) {
      if (settings.DO_LOGIN) {
        if (settings.getUserName() != null && settings.getPassword() != null) {
          login();
        }
        else {
          requestCredentials("Login failed, user name and password should not be empty", true);
        }
      }
    }
    else {
      requestCredentials(null, true);
    }

    StateStorageManager storageManager = ((ApplicationImpl)application).getStateStore().getStateStorageManager();
    registerProvider(storageManager, RoamingType.PER_USER);
    registerProvider(storageManager, RoamingType.PER_PLATFORM);
    registerProvider(storageManager, RoamingType.GLOBAL);
  }

  public void registerProjectLevelProviders(Project project) {
    String projectKey = getProjectId(project);
    StateStorageManager manager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    manager.registerStreamProvider(new ICSStreamProvider(projectKey, RoamingType.PER_PLATFORM), RoamingType.PER_PLATFORM);
    manager.registerStreamProvider(new ICSStreamProvider(projectKey, RoamingType.PER_USER), RoamingType.PER_USER);
  }

  private static void executeOnPooledThread(final Runnable runnable) {
    ourThreadExecutorsService.submit(runnable);
  }

  public void requestCredentials(final String failedMessage, boolean isStartupMode) {
    if (isStartupMode) {
      LoginDialog dialog = new LoginDialog(failedMessage);
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtil.updateWindowIcon(dialog);
      dialog.setVisible(true);
    }
    else {
      new LoginDialogWrapper(failedMessage).show();
      if (getIdeaServerSettings().getStatus() == IdeaConfigurationServerStatus.LOGGED_IN) {
        updateConfigFilesFromServer();
      }
    }
  }

  public void startPing() {
    updateAlarm.addRequest(updateRequest, 30 * 1000);
  }

  public void stopPing() {
    updateAlarm.cancelAllRequests();
  }

  private void registerProvider(StateStorageManager storageManager, RoamingType type) {
    storageManager.registerStreamProvider(new ICSStreamProvider(null, type) {
      @Override
      public String[] listSubFiles(final String fileSpec) {
        return serverConnector.listSubFileNames(IcsUrlBuilder.buildPath(fileSpec, roamingType, null));
      }

      @Override
      public void deleteFile(String fileSpec, RoamingType roamingType) {
        final String path = IcsUrlBuilder.buildPath(fileSpec, this.roamingType, null);
        executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              serverConnector.delete(path);
            }
            catch (IOException e) {
              LOG.debug(e);
            }
          }
        });
      }
    }, type);
  }

  public void login() {
    try {
      serverConnector = new IcsGitConnector();
      settings.setStatus(IdeaConfigurationServerStatus.LOGGED_IN);
    }
    catch (IOException e) {
      settings.setStatus(IdeaConfigurationServerStatus.CONNECTION_FAILED);
      LOG.error(e);
    }
  }

  public IdeaConfigurationServerSettings getIdeaServerSettings() {
    return settings;
  }

  public void logout() {
    settings.logout();
  }

  public static String getStatusText() {
    IdeaConfigurationServerSettings settings = getInstance().getIdeaServerSettings();
    IdeaConfigurationServerStatus serverStatus = settings.getStatus();
    switch (serverStatus) {
      case CONNECTION_FAILED:
        return "Connection failed";
      case LOGGED_IN:
        return "Logged in as " + settings.getUserName();
      case LOGGED_OUT:
        return "Logged out";
      case UNAUTHORIZED:
        return "Authorization failed";
      case UNKNOWN:
        return "Unknown status";
      default:
        return "Unknown";
    }
  }

  public static void updateConfigFilesFromServer() {
    final ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    StateStorageManager appStorageManager = app.getStateStore().getStateStorageManager();
    Collection<String> storageFileNames =
      appStorageManager.getStorageFileNames();
    if (!storageFileNames.isEmpty()) {
      HashSet<File> filesToUpdate = new HashSet<File>();
      processStorages(appStorageManager, storageFileNames, filesToUpdate);

      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project : projects) {
        StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();
        processStorages(storageManager, storageManager.getStorageFileNames(), filesToUpdate);
      }

      if (!filesToUpdate.isEmpty()) {
        LocalFileSystem.getInstance().refreshIoFiles(filesToUpdate);
      }


      SchemesManagerFactory.getInstance().updateConfigFilesFromStreamProviders();
    }
  }

  private static void processStorages(final StateStorageManager appStorageManager, final Collection<String> storageFileNames, Collection<File> filesToUpdate) {
    for (String storageFileName : storageFileNames) {
      StateStorage stateStorage = appStorageManager.getFileStateStorage(storageFileName);
      if (stateStorage instanceof FileBasedStorage) {
        try {
          FileBasedStorage fileBasedStorage = (FileBasedStorage)stateStorage;
          fileBasedStorage.resetProviderCache();
          File fileToUpdate = fileBasedStorage.updateFileExternallyFromStreamProviders();
          if (fileToUpdate != null) {
            filesToUpdate.add(fileToUpdate);
          }
        }
        catch (Throwable e) {
          LOG.debug(e);
        }
      }
    }
  }

  private class ICSStreamProvider implements CurrentUserHolder, StreamProvider {
    private final String projectId;
    // todo StreamProvider must be general and use passed roamingType
    protected final RoamingType roamingType;

    public ICSStreamProvider(@Nullable String projectId, @NotNull RoamingType roamingType) {
      this.projectId = projectId;
      this.roamingType = roamingType;
    }

    @Override
    public void saveContent(String fileSpec, @NotNull final InputStream content, long size, RoamingType roamingType, boolean async) throws IOException {
      final String path = IcsUrlBuilder.buildPath(fileSpec, this.roamingType, projectId);
      if (async) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              serverConnector.save(content, path);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        });
      }
      else {
        serverConnector.save(content, path);
      }
    }

    @Override
    @Nullable
    public InputStream loadContent(String fileSpec, RoamingType roamingType) throws IOException {
      if (projectId == null || roamingType == RoamingType.PER_PLATFORM) {
        return serverConnector.loadUserPreferences(IcsUrlBuilder.buildPath(fileSpec, this.roamingType, projectId));
      }
      else if (StoragePathMacros.WORKSPACE_FILE.equals(fileSpec)) {
        return serverConnector.loadUserPreferences(IcsUrlBuilder.buildPath(fileSpec, this.roamingType, projectId));
      }
      else {
        return null;
      }
    }

    @Override
    public String getCurrentUserName() {
      return settings.getUserName();
    }

    @Override
    public boolean isEnabled() {
      // todo configurable
      return true;
    }

    @Override
    public String[] listSubFiles(final String fileSpec) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void deleteFile(final String fileSpec, final RoamingType roamingType) {
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