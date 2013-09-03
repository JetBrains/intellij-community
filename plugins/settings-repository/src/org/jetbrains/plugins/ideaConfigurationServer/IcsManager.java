package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.FileBasedStorage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipOutputStream;

public class IcsManager {
  private static final Logger LOG = Logger.getInstance(IcsManager.class);

  private static final ExecutorService ourThreadExecutorsService = ConcurrencyUtil.newSingleThreadExecutor("IdeaServer executor");

  private static final String PROJECT_ID_KEY = "IDEA_SERVER_PROJECT_ID";

  private static final IcsManager instance = new IcsManager();

  private final IdeaConfigurationServerSettings settings;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final Runnable myUpdateRequest;
  private final File myLocalCopyDir;
  private Map<String, String> myProjectHashToKey;

  private IcsGitConnector serverConnector;

  public IcsManager() {
    settings = new IdeaConfigurationServerSettings();
    myLocalCopyDir = new File(PathManager.getSystemPath(), "ideaConfigurationServer");

    myUpdateRequest = new Runnable() {
      @Override
      public void run() {
        myUpdateAlarm.cancelAllRequests();
        try {
          try {
            if (settings.getStatus() == IdeaConfigurationServerStatus.CONNECTION_FAILED && settings.getUserName() != null && settings.getPassword() != null) {
              login();
            }
            else if (settings.getStatus() == IdeaConfigurationServerStatus.LOGGED_IN && settings.getUserName() != null && settings.getPassword() != null) {
              ping();
            }
          }
          catch (Exception e) {
            //ignore
          }
        }
        finally {
          myUpdateAlarm.addRequest(myUpdateRequest, 30 * 1000);
        }
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(StatusListener.TOPIC, new StatusListener() {
      @Override
      public void statusChanged(final IdeaConfigurationServerStatus status) {
        if (status == IdeaConfigurationServerStatus.LOGGED_IN) {
          if (!settings.wereSettingsSynchronized()) {
            try {
              backupConfig();
            }
            finally {
              settings.settingsWereSynchronized();
            }
          }
          try {
            saveSettingsLocalCopy(myLocalCopyDir);
          }
          catch (SocketTimeoutException timeout) {
            //ignore
          }
          catch (IOException e) {
            LOG.info(e); // Do not LOG.error() since server may respond with error code, like 503
          }
        }
      }
    });
  }

  public static IcsManager getInstance() {
    return instance;
  }

  private static void backupConfig() {
    String configPath = PathManager.getConfigPath();
    File configDir = new File(configPath);
    if (configDir.exists() && configDir.listFiles().length > 0) {
      File backupFile = createBackupFile(configDir);
      try {
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(backupFile));
        try {
          ZipUtil.addDirToZipRecursively(zipOutputStream,
                                         backupFile,
                                         configDir,
                                         "",
                                         null,
                                         null);
        }
        finally {
          zipOutputStream.close();
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
  }

  private static File createBackupFile(File configDir) {
    for (int i = 1; ; i++) {
      File candidate = getBackupFile(configDir, i);
      if (!candidate.exists()) {
        return candidate;
      }
    }
  }

  private static File getBackupFile(File configDir, int i) {
    if (i != 1) {
      return new File(configDir.getParentFile(), "idea_" + configDir.getName() + "_backup" + i + ".zip");
    }
    else {
      return new File(configDir.getParentFile(), "idea_" + configDir.getName() + "_backup.zip");
    }
  }

  private void saveSettingsLocalCopy(final File localCopyDir) throws IOException {
    if (localCopyDir.exists()) {
      FileUtil.delete(localCopyDir);
    }

    localCopyDir.mkdirs();

    IcsGitConnector.loadAllFiles(createBuilder("", null, null), new IcsGitConnector.ContentProcessor() {
      @Override
      public void processStream(InputStream line) throws IOException {
        File tempFile = FileUtil.createTempFile("temp", "settings");
        try {
          FileOutputStream out = new FileOutputStream(tempFile);
          try {
            FileUtil.copy(line, out);
          }
          finally {
            out.close();
          }

          if (tempFile.length() > 0) {
            ZipUtil.extract(tempFile, localCopyDir, null);
          }
        }
        finally {
          FileUtil.delete(tempFile);
        }
      }
    });

    myProjectHashToKey = readHashToKeyMap(getMappingsFile());

    //unzipFiles(localCopyDir);
  }

  private File getMappingsFile() {
    return new File(new File(myLocalCopyDir, "projects"), "mapping.txt");
  }

  @Nullable
  private InputStream loadUserPreferences(IdeaServerUrlBuilder builder) throws IOException {
    try {
      if (canUseLocalCopy(builder)) {
        File localFileCopy = new File(myLocalCopyDir, builder.buildPath());
        if (!localFileCopy.isFile()) {
          return null;
        }
        return new FileInputStream(localFileCopy);
      }
    }
    catch (FileNotFoundException ignored) {
    }
    return serverConnector.loadUserPreferences(builder.buildPath());
  }

  private static boolean canUseLocalCopy(IdeaServerUrlBuilder builder) {
    return builder.getRoamingType() != RoamingType.GLOBAL;
  }

  private void deleteUserPreferences(IdeaServerUrlBuilder builder) throws IOException {
    try {
      if (canUseLocalCopy(builder)) {
        File localCopy = new File(myLocalCopyDir, builder.buildPath());
        FileUtil.delete(localCopy);
      }
    }
    finally {
      IcsGitConnector.delete(builder);
    }
  }

  private String[] listSubFileNames(IdeaServerUrlBuilder urlBuilder) {
    try {
      if (canUseLocalCopy(urlBuilder)) {
        File localFile = new File(myLocalCopyDir, urlBuilder.buildPath());
        return collectSubFileNames(localFile);
      }
      else {
        return IcsGitConnector.listSubFileNames(urlBuilder);
      }
    }
    catch (Exception e) {
      try {
        return IcsGitConnector.listSubFileNames(urlBuilder);
      }
      catch (IOException e1) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    }
  }

  private static String[] collectSubFileNames(File localFile) {
    ArrayList<String> result = new ArrayList<String>();
    File[] files = localFile.listFiles();
    if (files != null) {
      for (File file : files) {
        result.add(file.getName());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  private static String ping(IdeaServerUrlBuilder urlBuilder) throws IOException {
    return IcsGitConnector.ping(urlBuilder);
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

  private IdeaServerUrlBuilder createBuilder(final String fileSpec, final RoamingType type, final String projectKey) {
    try {
      return new IdeaServerUrlBuilder(fileSpec, type, projectKey) {

        @Override
        public void setDisconnectedStatus() {
          settings.setStatus(IdeaConfigurationServerStatus.CONNECTION_FAILED);
        }

        @Override
        public void setUnauthorizedStatus() {
          settings.setStatus(IdeaConfigurationServerStatus.UNAUTHORIZED);
        }
      };
    }
    finally {
      if (projectKey != null) {
        checkIfProjectKeyMappingIsStored(projectKey);
      }
    }
  }

  private void checkIfProjectKeyMappingIsStored(String projectKey) {
    if (myProjectHashToKey != null) {
      String hash = String.valueOf(StringHash.calc(projectKey));
      if (!myProjectHashToKey.containsKey(hash)) {
        myProjectHashToKey.put(hash, projectKey);
        try {
          if (!saveHashToKeyMap()) {
            myProjectHashToKey.remove(hash);
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  private boolean saveHashToKeyMap() throws IOException {
    File mappingsFile = getMappingsFile();

    saveNewMappings(mappingsFile);

    FileInputStream input = new FileInputStream(mappingsFile);
    try {
      IcsGitConnector.send(mappingsFile, createBuilder("projects/mapping.txt",
                                                        RoamingType.PER_USER,
                                                        null));
      return true;
    }
    catch (Throwable t) {
      return false;
    }
    finally {
      input.close();
    }
  }

  private void saveNewMappings(File mappingsFile) throws IOException {
    FileOutputStream output = new FileOutputStream(mappingsFile);
    try {
      for (String hash : myProjectHashToKey.keySet()) {
        output.write((hash + " " + myProjectHashToKey.get(hash) + "\n").getBytes());
      }
    }
    finally {
      output.close();
    }
  }

  private static Map<String, String> readHashToKeyMap(File mappingFile) {
    LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();

    try {
      FileInputStream input = new FileInputStream(mappingFile);
      try {
        for (byte[] bytes : new LineReader(input).readLines()) {
          String line = new String(bytes);
          int separator = line.indexOf(" ");
          if (separator > 0) {
            String hash = line.substring(0, separator);
            String key = line.substring(separator + 1);

            if (hash.length() > 0 && key.length() > 0) {
              result.put(hash, key);
            }
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
      finally {
        try {
          input.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
    catch (FileNotFoundException e) {
      //ignore
    }

    return result;
  }

  public void deleteUserPreferencesAsync(final IdeaServerUrlBuilder builder) {
    executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          deleteUserPreferences(builder);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    });
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
    myUpdateAlarm.addRequest(myUpdateRequest, 30 * 1000);
  }

  public void stopPing() {
    myUpdateAlarm.cancelAllRequests();
  }

  private void registerProvider(StateStorageManager storageManager, RoamingType type) {
    storageManager.registerStreamProvider(new ICSStreamProvider(null, type) {
      @Override
      public String[] listSubFiles(final String fileSpec) {
        return listSubFileNames(createBuilder(fileSpec, roamingType, null));
      }

      @Override
      public void deleteFile(String fileSpec, RoamingType roamingType) {
        deleteUserPreferencesAsync(createBuilder(fileSpec, this.roamingType, null));
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

  public String ping() throws IOException {
    return ping(createBuilder(null, null, null));
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
      final String path = createBuilder(fileSpec, this.roamingType, projectId).buildPath();
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

    private IdeaServerUrlBuilder createBuilderInt(String fileSpec) {
      return createBuilder(fileSpec, roamingType, projectId);
    }

    @Override
    @Nullable
    public InputStream loadContent(String fileSpec, RoamingType roamingType) throws IOException {
      if (projectId == null || roamingType == RoamingType.PER_PLATFORM) {
        return loadUserPreferences(createBuilder(fileSpec, this.roamingType, projectId));
      }
      else if (StoragePathMacros.WORKSPACE_FILE.equals(fileSpec)) {
        return loadUserPreferences(createBuilderInt(fileSpec));
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
}