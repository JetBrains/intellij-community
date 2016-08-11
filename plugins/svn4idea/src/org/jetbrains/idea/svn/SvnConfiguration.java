/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
import org.jetbrains.idea.svn.auth.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.auth.SvnInteractiveAuthenticationProvider;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.config.SvnServerFileKeys;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.update.MergeRootInfo;
import org.jetbrains.idea.svn.update.UpdateRootInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

@State(
  name = "SvnConfiguration",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class SvnConfiguration implements PersistentStateComponent<SvnConfigurationState> {

  public final static int ourMaxAnnotateRevisionsDefault = 500;

  private final static long UPGRADE_TO_15_VERSION_ASKED = 123;
  private final static long CHANGELIST_SUPPORT = 124;
  private final static long UPGRADE_TO_16_VERSION_ASKED = 125;

  private final Project myProject;
  @NotNull private SvnConfigurationState myState = new SvnConfigurationState();

  private ISVNOptions myOptions;
  private SvnAuthenticationManager myAuthManager;
  private SvnAuthenticationManager myPassiveAuthManager;
  private SvnAuthenticationManager myInteractiveManager;

  public static final AuthStorage RUNTIME_AUTH_CACHE = new AuthStorage();

  private final Map<File, MergeRootInfo> myMergeRootInfos = new HashMap<>();
  private final Map<File, UpdateRootInfo> myUpdateRootInfos = new HashMap<>();
  private SvnInteractiveAuthenticationProvider myInteractiveProvider;
  private IdeaSVNConfigFile myServersFile;
  private SVNConfigFile myConfigFile;

  public boolean isCommandLine() {
    return UseAcceleration.commandLine.equals(getUseAcceleration());
  }

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
  private IdeaSVNConfigFile getServersFile() {
    if (myServersFile == null) {
      myServersFile = new IdeaSVNConfigFile(new File(getConfigurationDirectory(), IdeaSVNConfigFile.SERVERS_FILE_NAME));
    }
    myServersFile.updateGroups();

    return myServersFile;
  }

  @NotNull
  public SVNConfigFile getConfigFile() {
    if (myConfigFile == null) {
      myConfigFile = new SVNConfigFile(new File(getConfigurationDirectory(), IdeaSVNConfigFile.CONFIG_FILE_NAME));
    }
    
    return myConfigFile;
  }

  @NotNull
  public String getSshTunnelSetting() {
    // TODO: Check SVNCompositeConfigFile - to utilize both system and user settings
    return StringUtil.notNullize(getConfigFile().getPropertyValue("tunnels", "ssh"));
  }

  public void setSshTunnelSetting(@Nullable String value) {
    getConfigFile().setPropertyValue("tunnels", "ssh", value, true);
  }

  // uses configuration directory property - it should be saved first
  public void setHttpTimeout(final long value) {
    long cut = value / 1000;
    getServersFile().setValue("global", SvnServerFileKeys.TIMEOUT, String.valueOf(cut));
    getServersFile().save();
  }

  public static SvnConfiguration getInstance(final Project project) {
    return ServiceManager.getService(project, SvnConfiguration.class);
  }

  public SvnConfiguration(final Project project) {
    myProject = project;
  }

  public void setIgnoreSpacesInAnnotate(final boolean value) {
    final boolean changed = myState.IGNORE_SPACES_IN_ANNOTATE != value;
    myState.IGNORE_SPACES_IN_ANNOTATE = value;
    if (changed) {
      getProject().getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(SvnVcs.getKey());
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

  public UseAcceleration getUseAcceleration() {
    return myState.accelerationType;
  }

  public void setUseAcceleration(UseAcceleration useAcceleration) {
    myState.accelerationType = useAcceleration;
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

  public boolean isUpdateLockOnDemand() {
    return myState.UPDATE_LOCK_ON_DEMAND;
  }

  public void setUpdateLockOnDemand(boolean updateLockOnDemand) {
    myState.UPDATE_LOCK_ON_DEMAND = updateLockOnDemand;
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
    if (myState.directory.path == null || isUseDefaultConfiguation()) {
      myState.directory.path = IdeaSubversionConfigurationDirectory.getPath();
    }
    return myState.directory.path;
  }

  public boolean isUseDefaultConfiguation() {
    return myState.directory.useDefault;
  }

  public void setConfigurationDirParameters(final boolean newUseDefault, final String newConfigurationDirectory) {
    final String defaultPath = IdeaSubversionConfigurationDirectory.getPath();
    final String oldEffectivePath = isUseDefaultConfiguation() ? defaultPath : getConfigurationDirectory();
    final String newEffectivePath = newUseDefault ? defaultPath : newConfigurationDirectory;

    boolean directoryChanged = !Comparing.equal(getConfigurationDirectory(), newConfigurationDirectory);
    if (directoryChanged) {
      setConfigurationDirectory(newConfigurationDirectory);
    }
    boolean usageChanged = isUseDefaultConfiguation() != newUseDefault;
    if (usageChanged) {
      setUseDefaultConfiguation(newUseDefault);
    }

    if (directoryChanged || usageChanged) {
      if (! Comparing.equal(oldEffectivePath, newEffectivePath)) {
        clear();
      }
    }
  }

  private void setConfigurationDirectory(String path) {
    myState.directory.path = path;
    File dir = path == null ? new File(IdeaSubversionConfigurationDirectory.getPath()) : new File(path);
    SVNConfigFile.createDefaultConfiguration(dir);
  }

  public void clear() {
    myOptions = null;
    myAuthManager = null;
    myPassiveAuthManager = null;
    myInteractiveManager = null;
    myInteractiveProvider = null;
    RUNTIME_AUTH_CACHE.clear();
  }

  private void setUseDefaultConfiguation(boolean useDefault) {
    myState.directory.useDefault = useDefault;
  }

  public ISVNOptions getOptions() {
    if (myOptions == null) {
      File path = new File(getConfigurationDirectory());
      myOptions = SVNWCUtil.createDefaultOptions(path.getAbsoluteFile(), true);
    }
    return myOptions;
  }

  public SvnAuthenticationManager getAuthenticationManager(final SvnVcs svnVcs) {
    if (myAuthManager == null) {
      // reloaded when configuration directory changes
        myAuthManager = new SvnAuthenticationManager(svnVcs.getProject(), new File(getConfigurationDirectory()));
      Disposer.register(svnVcs.getProject(), new Disposable() {
        @Override
        public void dispose() {
          myAuthManager = null;
        }
      });
      getInteractiveManager(svnVcs);
      // to init
      myAuthManager.setAuthenticationProvider(new SvnAuthenticationProvider(svnVcs, myInteractiveProvider, myAuthManager));
      myAuthManager.setRuntimeStorage(RUNTIME_AUTH_CACHE);
    }
    return myAuthManager;
  }

  public SvnAuthenticationManager getPassiveAuthenticationManager(Project project) {
    if (myPassiveAuthManager == null) {
      myPassiveAuthManager = new SvnAuthenticationManager(project, new File(getConfigurationDirectory()));
      myPassiveAuthManager.setAuthenticationProvider(new ISVNAuthenticationProvider() {
        @Override
        public SVNAuthentication requestClientAuthentication(String kind,
                                                             SVNURL url,
                                                             String realm,
                                                             SVNErrorMessage errorMessage,
                                                             SVNAuthentication previousAuth,
                                                             boolean authMayBeStored) {
          return null;
        }

        @Override
        public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
          return REJECTED;
        }
      });
      myPassiveAuthManager.setRuntimeStorage(RUNTIME_AUTH_CACHE);
    }
    return myPassiveAuthManager;
  }

  public SvnAuthenticationManager getInteractiveManager(final SvnVcs svnVcs) {
    if (myInteractiveManager == null) {
      myInteractiveManager = new SvnAuthenticationManager(svnVcs.getProject(), new File(getConfigurationDirectory()));
      myInteractiveManager.setRuntimeStorage(RUNTIME_AUTH_CACHE);
      myInteractiveProvider = new SvnInteractiveAuthenticationProvider(svnVcs, myInteractiveManager);
      myInteractiveManager.setAuthenticationProvider(myInteractiveProvider);
    }
    return myInteractiveManager;
  }

  public void getServerFilesManagers(final Ref<SvnServerFileManager> systemManager, final Ref<SvnServerFileManager> userManager) {
    // created only if does not exist
    final File dir = new File(getConfigurationDirectory());
    if (! dir.exists()) {
      SVNConfigFile.createDefaultConfiguration(dir);
    }

    systemManager.set(new SvnServerFileManagerImpl(new IdeaSVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), IdeaSVNConfigFile.SERVERS_FILE_NAME))));
    userManager.set(new SvnServerFileManagerImpl(getServersFile()));
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

  public boolean isIsUseDefaultProxy() {
    return myState.useDefaultProxy;
  }

  public void setIsUseDefaultProxy(final boolean isUseDefaultProxy) {
    myState.useDefaultProxy = isUseDefaultProxy;
  }

  // TODO: Rewrite AutoStorage to use MemoryPasswordSafe at least
  public static class AuthStorage implements ISVNAuthenticationStorage {

    private final TreeSet<String> myKeys = ContainerUtil.newTreeSet();
    private final Map<String, Object> myStorage = ContainerUtil.newHashMap();

    @NotNull
    public static String getKey(@NotNull String type, @NotNull String realm) {
      return type + "$" + realm;
    }

    public synchronized void clear() {
      myStorage.clear();
      myKeys.clear();
    }

    public synchronized void putData(String kind, String realm, Object data) {
      String key = getKey(kind, realm);

      if (data == null) {
        myStorage.remove(key);
        myKeys.remove(key);
      } else {
        myStorage.put(key, data);
        myKeys.add(key);
      }
    }

    public synchronized Object getData(String kind, String realm) {
      return myStorage.get(getKey(kind, realm));
    }

    public synchronized Object getDataWithLowerCheck(String kind, String realm) {
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

  public void acknowledge(final String kind, final String realm, final Object object) {
    RUNTIME_AUTH_CACHE.putData(kind, realm, object);
  }

  public void clearCredentials(final String kind, final String realm) {
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

  public enum UseAcceleration {
    commandLine,
    nothing
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
