// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.config.SvnIniFile;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.update.MergeRootInfo;
import org.jetbrains.idea.svn.update.UpdateRootInfo;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.jetbrains.idea.svn.SvnUtil.USER_CONFIGURATION_PATH;
import static org.jetbrains.idea.svn.config.ServersFileKeys.GLOBAL_SERVER_GROUP;
import static org.jetbrains.idea.svn.config.ServersFileKeys.TIMEOUT;
import static org.jetbrains.idea.svn.config.SvnIniFile.CONFIG_FILE_NAME;
import static org.jetbrains.idea.svn.config.SvnIniFile.SERVERS_FILE_NAME;

@State(name = "SvnConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE), reportStatistic = false)
public class SvnConfiguration implements PersistentStateComponent<SvnConfigurationState>, Disposable {
  public final static int ourMaxAnnotateRevisionsDefault = 500;

  private final static long UPGRADE_TO_15_VERSION_ASKED = 123;
  private final static long CHANGELIST_SUPPORT = 124;
  private final static long UPGRADE_TO_16_VERSION_ASKED = 125;

  private final Project myProject;
  @NotNull
  private SvnConfigurationState myState = new SvnConfigurationState();

  private SvnAuthenticationManager myAuthManager;
  private SvnAuthenticationManager myPassiveAuthManager;
  private SvnAuthenticationManager myInteractiveManager;

  public static final AuthStorage RUNTIME_AUTH_CACHE = new AuthStorage();

  private final Map<File, MergeRootInfo> myMergeRootInfos = new HashMap<>();
  private final Map<File, UpdateRootInfo> myUpdateRootInfos = new HashMap<>();
  private SvnInteractiveAuthenticationProvider myInteractiveProvider;
  private SvnIniFile myServersFile;
  private SvnIniFile myConfigFile;

  @NotNull
  @Override
  public SvnConfigurationState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull SvnConfigurationState state) {
    myState = state;
  }

  public long getHttpTimeout() {
    final String timeout = getServersFile().getDefaultGroup().getTimeout();
    try {
      return Long.parseLong(timeout) * 1000;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @NotNull
  public DiffOptions getMergeOptions() {
    return new DiffOptions(isIgnoreSpacesInMerge(), isIgnoreSpacesInMerge(), isIgnoreSpacesInMerge());
  }

  @NotNull
  public SvnIniFile getServersFile() {
    if (myServersFile == null) {
      myServersFile = new SvnIniFile(getConfigurationPath().resolve(SERVERS_FILE_NAME));
    }
    myServersFile.updateGroups();

    return myServersFile;
  }

  @NotNull
  public SvnIniFile getConfigFile() {
    if (myConfigFile == null) {
      myConfigFile = new SvnIniFile(getConfigurationPath().resolve(CONFIG_FILE_NAME));
    }

    return myConfigFile;
  }

  @NotNull
  public String getSshTunnelSetting() {
    // TODO: Utilize both system and user settings
    return StringUtil.notNullize(getConfigFile().getValue("tunnels", "ssh"));
  }

  public void setSshTunnelSetting(@Nullable String value) {
    getConfigFile().setValue("tunnels", "ssh", value);
    getConfigFile().save();
  }

  // uses configuration directory property - it should be saved first
  public void setHttpTimeout(final long value) {
    long cut = value / 1000;
    getServersFile().setValue(GLOBAL_SERVER_GROUP, TIMEOUT, String.valueOf(cut));
    getServersFile().save();
  }

  public static SvnConfiguration getInstance(final Project project) {
    return project.getService(SvnConfiguration.class);
  }

  public SvnConfiguration(final Project project) {
    myProject = project;
  }

  public void setIgnoreSpacesInAnnotate(final boolean value) {
    final boolean changed = myState.IGNORE_SPACES_IN_ANNOTATE != value;
    myState.IGNORE_SPACES_IN_ANNOTATE = value;
    if (changed) {
      BackgroundTaskUtil.syncPublisher(getProject(), VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(SvnVcs.getKey());
    }
  }

  public long getSshConnectionTimeout() {
    return myState.sshConnectionTimeout;
  }

  public void setSshConnectionTimeout(long sshConnectionTimeout) {
    myState.sshConnectionTimeout = sshConnectionTimeout;
  }

  public long getSshReadTimeout() {
    return myState.sshReadTimeout;
  }

  public void setSshReadTimeout(long sshReadTimeout) {
    myState.sshReadTimeout = sshReadTimeout;
  }

  public Project getProject() {
    return myProject;
  }

  public Boolean isKeepNewFilesAsIsForTreeConflictMerge() {
    return myState.keepNewFilesAsIsForTreeConflictMerge;
  }

  public void setKeepNewFilesAsIsForTreeConflictMerge(Boolean keepNewFilesAsIsForTreeConflictMerge) {
    myState.keepNewFilesAsIsForTreeConflictMerge = keepNewFilesAsIsForTreeConflictMerge;
  }

  public SSLProtocols getSslProtocols() {
    return myState.sslProtocols;
  }

  public void setSslProtocols(SSLProtocols sslProtocols) {
    myState.sslProtocols = sslProtocols;
  }

  public Depth getUpdateDepth() {
    return myState.UPDATE_DEPTH;
  }

  public void setUpdateDepth(Depth updateDepth) {
    myState.UPDATE_DEPTH = updateDepth;
  }

  public boolean isRunUnderTerminal() {
    return myState.runUnderTerminal;
  }

  public void setRunUnderTerminal(boolean value) {
    myState.runUnderTerminal = value;
  }

  public boolean isIgnoreExternals() {
    return myState.IGNORE_EXTERNALS;
  }

  public void setIgnoreExternals(boolean ignoreExternals) {
    myState.IGNORE_EXTERNALS = ignoreExternals;
  }

  public boolean isMergeDryRun() {
    return myState.MERGE_DRY_RUN;
  }

  public void setMergeDryRun(boolean mergeDryRun) {
    myState.MERGE_DRY_RUN = mergeDryRun;
  }

  public boolean isMergeDiffUseAncestry() {
    return myState.MERGE_DIFF_USE_ANCESTRY;
  }

  public void setMergeDiffUseAncestry(boolean mergeDiffUseAncestry) {
    myState.MERGE_DIFF_USE_ANCESTRY = mergeDiffUseAncestry;
  }

  public boolean isIgnoreSpacesInMerge() {
    return myState.IGNORE_SPACES_IN_MERGE;
  }

  public void setIgnoreSpacesInMerge(boolean ignoreSpacesInMerge) {
    myState.IGNORE_SPACES_IN_MERGE = ignoreSpacesInMerge;
  }

  public boolean isCheckNestedForQuickMerge() {
    return myState.CHECK_NESTED_FOR_QUICK_MERGE;
  }

  public void setCheckNestedForQuickMerge(boolean checkNestedForQuickMerge) {
    myState.CHECK_NESTED_FOR_QUICK_MERGE = checkNestedForQuickMerge;
  }

  public boolean isIgnoreSpacesInAnnotate() {
    return myState.IGNORE_SPACES_IN_ANNOTATE;
  }

  public boolean isShowMergeSourcesInAnnotate() {
    return myState.SHOW_MERGE_SOURCES_IN_ANNOTATE;
  }

  public void setShowMergeSourcesInAnnotate(boolean showMergeSourcesInAnnotate) {
    myState.SHOW_MERGE_SOURCES_IN_ANNOTATE = showMergeSourcesInAnnotate;
  }

  public boolean isForceUpdate() {
    return myState.FORCE_UPDATE;
  }

  public void setForceUpdate(boolean forceUpdate) {
    myState.FORCE_UPDATE = forceUpdate;
  }

  private static Long fixSupportedVersion(final Long version) {
    return version == null || version.longValue() < CHANGELIST_SUPPORT
           ? UPGRADE_TO_15_VERSION_ASKED
           : version;
  }

  public boolean changeListsSynchronized() {
    ensureSupportedVersion();
    return myState.supportedVersion != null && myState.supportedVersion >= CHANGELIST_SUPPORT;
  }

  public void upgrade() {
    myState.supportedVersion = UPGRADE_TO_16_VERSION_ASKED;
  }

  private void ensureSupportedVersion() {
    if (myState.supportedVersion == null) {
      myState.supportedVersion = fixSupportedVersion(SvnBranchConfigurationManager.getInstance(myProject).getSupportValue());
    }
  }

  public String getConfigurationDirectory() {
    if (myState.directory.path == null || isUseDefaultConfiguration()) {
      myState.directory.path = USER_CONFIGURATION_PATH.getValue().toString();
    }
    return myState.directory.path;
  }

  @NotNull
  public Path getConfigurationPath() {
    return Paths.get(getConfigurationDirectory());
  }

  public boolean isUseDefaultConfiguration() {
    return myState.directory.useDefault;
  }

  public void setConfigurationDirParameters(final boolean newUseDefault, final String newConfigurationDirectory) {
    final String defaultPath = USER_CONFIGURATION_PATH.getValue().toString();
    final String oldEffectivePath = isUseDefaultConfiguration() ? defaultPath : getConfigurationDirectory();
    final String newEffectivePath = newUseDefault ? defaultPath : newConfigurationDirectory;

    boolean directoryChanged = !Objects.equals(getConfigurationDirectory(), newConfigurationDirectory);
    if (directoryChanged) {
      myState.directory.path = newConfigurationDirectory;
    }
    boolean usageChanged = isUseDefaultConfiguration() != newUseDefault;
    if (usageChanged) {
      setUseDefaultConfiguration(newUseDefault);
    }

    if (directoryChanged || usageChanged) {
      if (!Objects.equals(oldEffectivePath, newEffectivePath)) {
        clear();
      }
    }
  }

  @Override
  public void dispose() {
    clear();
  }

  private void clear() {
    myAuthManager = null;
    myPassiveAuthManager = null;
    myInteractiveManager = null;
    myInteractiveProvider = null;
    RUNTIME_AUTH_CACHE.clear();
  }

  private void setUseDefaultConfiguration(boolean useDefault) {
    myState.directory.useDefault = useDefault;
  }

  public SvnAuthenticationManager getAuthenticationManager(@NotNull SvnVcs vcs) {
    if (myAuthManager == null) {
      // reloaded when configuration directory changes
      myAuthManager = new SvnAuthenticationManager(vcs.getProject(), getConfigurationPath());
      getInteractiveManager(vcs);
      // to init
      myAuthManager.setAuthenticationProvider(new SvnAuthenticationProvider(vcs, myInteractiveProvider, myAuthManager));
    }
    return myAuthManager;
  }

  public SvnAuthenticationManager getPassiveAuthenticationManager(@NotNull SvnVcs vcs) {
    if (myPassiveAuthManager == null) {
      myPassiveAuthManager = new SvnAuthenticationManager(vcs.getProject(), getConfigurationPath());
      myPassiveAuthManager.setAuthenticationProvider(new AuthenticationProvider() {
        @Override
        public AuthenticationData requestClientAuthentication(String kind, Url url, String realm, boolean canCache) {
          return null;
        }

        @Override
        public AcceptResult acceptServerAuthentication(Url url, String realm, Object certificate, boolean canCache) {
          return AcceptResult.REJECTED;
        }
      });
    }
    return myPassiveAuthManager;
  }

  public SvnAuthenticationManager getInteractiveManager(@NotNull SvnVcs vcs) {
    if (myInteractiveManager == null) {
      myInteractiveManager = new SvnAuthenticationManager(vcs.getProject(), getConfigurationPath());
      myInteractiveProvider = new SvnInteractiveAuthenticationProvider(vcs, myInteractiveManager);
      myInteractiveManager.setAuthenticationProvider(myInteractiveProvider);
    }
    return myInteractiveManager;
  }

  public boolean isAutoUpdateAfterCommit() {
    return myState.autoUpdateAfterCommit;
  }

  public void setAutoUpdateAfterCommit(boolean autoUpdateAfterCommit) {
    myState.autoUpdateAfterCommit = autoUpdateAfterCommit;
  }

  public boolean isKeepLocks() {
    return myState.keepLocks;
  }

  public void setKeepLocks(boolean keepLocks) {
    myState.keepLocks = keepLocks;
  }

  public boolean isUseDefaultProxy() {
    return myState.useDefaultProxy;
  }

  public void setUseDefaultProxy(final boolean isUseDefaultProxy) {
    myState.useDefaultProxy = isUseDefaultProxy;
  }

  // TODO: Rewrite AutoStorage to use MemoryPasswordSafe at least
  public static class AuthStorage {

    @NotNull private final TreeSet<String> myKeys = new TreeSet<>();
    @NotNull private final Map<String, Object> myStorage = new HashMap<>();

    @NotNull
    public static String getKey(@NotNull String type, @NotNull String realm) {
      return type + "$" + realm;
    }

    public synchronized void clear() {
      myStorage.clear();
      myKeys.clear();
    }

    public synchronized void putData(@NotNull String kind, @NotNull String realm, @Nullable Object data) {
      String key = getKey(kind, realm);

      if (data == null) {
        myStorage.remove(key);
        myKeys.remove(key);
      } else {
        myStorage.put(key, data);
        myKeys.add(key);
      }
    }

    @Nullable
    public synchronized Object getDataWithLowerCheck(@NotNull String kind, @NotNull String realm) {
      String key = getKey(kind, realm);
      Object result = myStorage.get(key);

      if (result == null) {
        String lowerKey = myKeys.lower(key);

        if (lowerKey != null && key.startsWith(lowerKey)) {
          result = myStorage.get(lowerKey);
        }
      }

      return result;
    }
  }

  @NotNull
  public MergeRootInfo getMergeRootInfo(final File file, final SvnVcs svnVcs) {
    if (!myMergeRootInfos.containsKey(file)) {
      myMergeRootInfos.put(file, new MergeRootInfo(file, svnVcs));
    }
    return myMergeRootInfos.get(file);
  }

  public UpdateRootInfo getUpdateRootInfo(File file, final SvnVcs svnVcs) {
    if (!myUpdateRootInfos.containsKey(file)) {
      myUpdateRootInfos.put(file, new UpdateRootInfo(file, svnVcs));
    }
    return myUpdateRootInfos.get(file);
  }

  // TODO: Check why SvnUpdateEnvironment.validationOptions is fully commented and then remove this method if necessary
  public Map<File, UpdateRootInfo> getUpdateInfosMap() {
    return Collections.unmodifiableMap(myUpdateRootInfos);
  }

  public void acknowledge(@NotNull String kind, @NotNull String realm, @Nullable Object object) {
    RUNTIME_AUTH_CACHE.putData(kind, realm, object);
  }

  public void clearCredentials(@NotNull String kind, @NotNull String realm) {
    RUNTIME_AUTH_CACHE.putData(kind, realm, null);
  }

  public void clearRuntimeStorage() {
    RUNTIME_AUTH_CACHE.clear();
  }

  public int getMaxAnnotateRevisions() {
    return myState.maxAnnotateRevisions;
  }

  public void setMaxAnnotateRevisions(int maxAnnotateRevisions) {
    myState.maxAnnotateRevisions = maxAnnotateRevisions;
  }

  public boolean isCleanupRun() {
    return myState.cleanupOnStartRun;
  }

  public void setCleanupRun(boolean cleanupRun) {
    myState.cleanupOnStartRun = cleanupRun;
  }

  public enum SSLProtocols {
    sslv3, tlsv1, all
  }

  public enum SshConnectionType {
    PASSWORD,
    PRIVATE_KEY,
    SUBVERSION_CONFIG
  }
}
