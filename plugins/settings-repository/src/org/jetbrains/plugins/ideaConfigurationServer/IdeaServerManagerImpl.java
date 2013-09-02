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
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipOutputStream;

public class IdeaServerManagerImpl {
  private static final Logger LOG = Logger.getInstance(IdeaServerManagerImpl.class);

  private static final String PROJECT_ID_KEY = "IDEA_SERVER_PROJECT_ID";

  private final IdeaServerSettings mySettings;
  private static final IdeaServerManagerImpl ourInstance = new IdeaServerManagerImpl();
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final Runnable myUpdateRequest;
  private final File myLocalCopyDir;
  private Map<String, String> myProjectHashToKey = null;

  public IdeaServerManagerImpl() {
    mySettings = new IdeaServerSettings();
    myLocalCopyDir = new File(new File(PathManager.getSystemPath(), "ideaServer"), "localCopy");

    myUpdateRequest = new Runnable() {
      @Override
      public void run() {
        myUpdateAlarm.cancelAllRequests();
        try {
          try {
            if (mySettings.getStatus() == IdeaServerStatus.CONNECTION_FAILED && mySettings.getUserName() != null && mySettings.getPassword() != null) {
              login();
            }
            else if (mySettings.getStatus() == IdeaServerStatus.LOGGED_IN && mySettings.getUserName() != null && mySettings.getPassword() != null) {
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

    mySettings.addStatusListener(new StatusListener() {
      @Override
      public void statusChanged(final IdeaServerStatus status) {
        if (status == IdeaServerStatus.LOGGED_IN) {
          if (!mySettings.wereSettingsSynchronized()) {
            try {
              backupConfig();
            }
            finally {
              mySettings.settingsWereSynchronized();
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

    IdeaServerConnector.loadAllFiles(createBuilder("", null, null), new IdeaServerConnector.ContentProcessor() {
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
    catch (FileNotFoundException e) {
      return IdeaServerConnector.loadUserPreferences(builder);
    }
    return IdeaServerConnector.loadUserPreferences(builder);
  }

  private boolean canUseLocalCopy(IdeaServerUrlBuilder builder) {
    return myLocalCopyDir.isDirectory() && builder.getRoamingType() != RoamingType.GLOBAL;
  }

  private static void saveUserPreferences(File file, IdeaServerUrlBuilder builder) throws IOException {
    IdeaServerConnector.send(file, builder);
  }

  private void deleteUserPreferences(IdeaServerUrlBuilder builder) throws IOException {
    try {
      if (canUseLocalCopy(builder)) {
        File localCopy = new File(myLocalCopyDir, builder.buildPath());
        FileUtil.delete(localCopy);
      }
    }
    finally {
      IdeaServerConnector.delete(builder);
    }
  }

  private String[] listSubFileNames(IdeaServerUrlBuilder urlBuilder) {
    try {
      if (canUseLocalCopy(urlBuilder)) {
        File localFile = new File(myLocalCopyDir, urlBuilder.buildPath());
        return collectSubFileNames(localFile);
      }
      else {
        return IdeaServerConnector.listSubFileNames(urlBuilder);
      }
    }
    catch (Exception e) {
      try {
        return IdeaServerConnector.listSubFileNames(urlBuilder);
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

  public static String login(IdeaServerUrlBuilder urlBuilder) throws IOException {
    return IdeaServerConnector.login(urlBuilder);
  }

  private static String ping(IdeaServerUrlBuilder urlBuilder) throws IOException {
    return IdeaServerConnector.ping(urlBuilder);
  }

  private static String getProjectKey(final Project project) {
    String id = PropertiesComponent.getInstance(project).getValue(PROJECT_ID_KEY);
    if (id == null) {
      id = UUID.randomUUID().toString();
      PropertiesComponent.getInstance(project).setValue(PROJECT_ID_KEY, id);
    }
    return id;
  }

  public void registerProjectLevelProviders(Project project) {
    final String projectKey = getProjectKey(project);
    final StateStorageManager manager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    manager.registerStreamProvider(new MyStreamProvider() {
      @Override
      public void saveContent(String fileSpec, @NotNull InputStream content, long size, RoamingType roamingType, boolean async) throws IOException {
        saveFileContent(content, size, createBuilder(fileSpec, RoamingType.PER_PLATFORM, projectKey), async);
      }

      @Override
      protected IdeaServerUrlBuilder createBuilderInt(final String fileSpec) {
        return createBuilder(fileSpec, RoamingType.PER_PLATFORM, projectKey);
      }

      @Override
      public InputStream loadContent(final String fileSpec, final RoamingType roamingType) throws IOException {
        return loadUserPreferences(createBuilder(fileSpec, RoamingType.PER_PLATFORM, projectKey));
      }
    }, RoamingType.PER_PLATFORM);

    manager.registerStreamProvider(new MyStreamProvider() {
      @Override
      public void saveContent(final String fileSpec, @NotNull final InputStream content, final long size, final RoamingType roamingType,
                              boolean async) throws IOException {

        saveFileContent(content, size, createBuilder(fileSpec, RoamingType.PER_USER, projectKey), async);
      }

      @Override
      protected IdeaServerUrlBuilder createBuilderInt(final String fileSpec) {
        return createBuilder(fileSpec, RoamingType.PER_USER, projectKey);
      }

      @Override
      public InputStream loadContent(final String fileSpec, final RoamingType roamingType) throws IOException {
        if (StoragePathMacros.WORKSPACE_FILE.equals(fileSpec)) {
          return loadUserPreferences(createBuilderInt(fileSpec));
        }
        else {
          return null;
        }
      }
    }, RoamingType.PER_USER);
  }

  private IdeaServerUrlBuilder createBuilder(final String fileSpec, final RoamingType type, final String projectKey) {
    try {
      return new IdeaServerUrlBuilder(fileSpec, type, projectKey, mySettings.getSessionId(), mySettings.getUserName(), mySettings.getPassword()) {
        @Override
        protected void onSessionIdUpdated(final String id) {
          mySettings.updateSession(id);
        }

        @Override
        public void setDisconnectedStatus() {
          mySettings.setStatus(IdeaServerStatus.CONNECTION_FAILED);
        }

        @Override
        public void setUnauthorizedStatus() {
          mySettings.setStatus(IdeaServerStatus.UNAUTHORIZED);
        }
      }.setReloginAutomatically(true);
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
      saveUserPreferences(mappingsFile, createBuilder("projects/mapping.txt",
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

  public static void saveUserPreferencesAsync(final File file, final IdeaServerUrlBuilder builder) {
    executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          saveUserPreferences(file, builder);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
        finally {
          FileUtil.delete(file);
        }
      }
    });
  }

  private static File copyStreamToTempFile(final InputStream bytes, final long size) throws IOException {
    File tempFile = FileUtil.createTempFile("configr", "temp");
    try {
      FileOutputStream out = new FileOutputStream(tempFile);
      try {
        FileUtil.copy(bytes, (int)size, out);
      }
      finally {
        out.close();
      }

      return tempFile;
    }
    catch (IOException e) {
      FileUtil.delete(tempFile);
      throw e;
    }
    catch (Throwable e) {
      FileUtil.delete(tempFile);
      LOG.info(e);
      throw new IOException(e.getLocalizedMessage());
    }
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

  private static final ExecutorService ourThreadExecutorsService = ConcurrencyUtil.newSingleThreadExecutor("IdeaServer executor");


  private static void executeOnPooledThread(final Runnable runnable) {
    ourThreadExecutorsService.submit(runnable);
  }

  public void registerApplicationLevelProviders(final Application application) {
    mySettings.loadCredentials();

    if (mySettings.REMEMBER_SETTINGS || GraphicsEnvironment.isHeadless()) {
      if (mySettings.DO_LOGIN) {
        if (mySettings.getUserName() != null && mySettings.getPassword() != null) {
          performLogin();
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

  public void performLogin() {
    String failedMessage = null;
    try {
      login();
    }
    catch (ConnectException e) {
      ErrorMessageDialog.show("Login to IntelliJ Configuration Server", e.getLocalizedMessage(), true);
      return;
    }
    catch (Exception e) {
      failedMessage = e.getLocalizedMessage();
    }

    if (mySettings.getSessionId() == null) {
      requestCredentials(failedMessage, true);
    }
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
      if (getIdeaServerSettings().getStatus() == IdeaServerStatus.LOGGED_IN) {
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

  abstract class MyStreamProvider implements CurrentUserHolder, StreamProvider {
    @Override
    public String getCurrentUserName() {
      return mySettings.getUserName();
    }

    @Override
    public boolean isEnabled() {
      return areProvidersEnabled();
    }

    @Override
    public String[] listSubFiles(final String fileSpec) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void deleteFile(final String fileSpec, final RoamingType roamingType) {
      //do nothing
    }

    protected abstract IdeaServerUrlBuilder createBuilderInt(String fileSpec);
  }

  private void registerProvider(final StateStorageManager storageManager, final RoamingType type) {
    storageManager.registerStreamProvider(new MyStreamProvider() {
      @Override
      public void saveContent(final String fileSpec, @NotNull final InputStream content, final long size, final RoamingType roamingType,
                              boolean async) throws IOException {

        saveFileContent(content, size, createBuilder(fileSpec, type, null), async);
      }

      @Override
      public InputStream loadContent(final String fileSpec, final RoamingType roamingType) throws IOException {
        return loadUserPreferences(createBuilder(fileSpec, type, null));
      }

      @Override
      public String[] listSubFiles(final String fileSpec) {
        return listSubFileNames(createBuilder(fileSpec, type, null));
      }

      @Override
      public void deleteFile(final String fileSpec, final RoamingType roamingType) {
        deleteUserPreferencesAsync(createBuilder(fileSpec, type, null));
      }

      @Override
      protected IdeaServerUrlBuilder createBuilderInt(final String fileSpec) {
        return createBuilder(fileSpec, type, null);
      }
    }, type);
  }

  private void saveFileContent(InputStream content, long size, IdeaServerUrlBuilder builder, boolean async) throws IOException {
    File tmpFile = null;
    try {
      if (canUseLocalCopy(builder)) {
        File localFile = new File(myLocalCopyDir, builder.buildPath());
        localFile.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(localFile);
        try {
          FileUtil.copy(content, (int)size, out);
        }
        finally {
          out.close();
        }
        tmpFile = FileUtil.createTempFile("configr", "temp");
        FileUtil.copy(localFile, tmpFile);
      }
      else {
        tmpFile = copyStreamToTempFile(content, size);
      }
    }
    finally {
      if (tmpFile != null) {
        if (async) {
          saveUserPreferencesAsync(tmpFile, builder);
        }
        else {
          try {
            saveUserPreferences(tmpFile, builder);
          }
          finally {
            FileUtil.delete(tmpFile);
          }
        }
      }
    }
  }

  private boolean areProvidersEnabled() {
    return mySettings.getSessionId() != null && mySettings.getStatus() == IdeaServerStatus.LOGGED_IN;
  }

  public void login() throws Exception {
    try {
      String sessionId = login(createBuilder(null, null, null));
      if (sessionId != null) {
        mySettings.updateSession(sessionId);
        mySettings.setStatus(IdeaServerStatus.LOGGED_IN);
      }
    }
    catch (Exception e) {
      mySettings.setStatus(IdeaServerStatus.CONNECTION_FAILED);
      throw e;
    }
  }

  public String ping() throws IOException {
    return ping(createBuilder(null, null, null));
  }

  public static IdeaServerManagerImpl getInstance() {
    return ourInstance;
  }

  public IdeaServerSettings getIdeaServerSettings() {
    return mySettings;
  }

  public void logout() {
    mySettings.logout();
  }

  public static String getStatusText() {
    IdeaServerSettings settings = getInstance().getIdeaServerSettings();
    IdeaServerStatus serverStatus = settings.getStatus();
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
}
