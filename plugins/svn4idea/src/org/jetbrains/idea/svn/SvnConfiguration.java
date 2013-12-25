/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.idea.svn.config.SvnServerFileKeys;
import org.jetbrains.idea.svn.dialogs.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.dialogs.SvnInteractiveAuthenticationProvider;
import org.jetbrains.idea.svn.update.MergeRootInfo;
import org.jetbrains.idea.svn.update.UpdateRootInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.*;

@State(
  name = "SvnConfiguration",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )
  }
)
public class SvnConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnConfiguration");
  public final static int ourMaxAnnotateRevisionsDefault = 500;

  public static final String CLEANUP_ON_START_RUN = "cleanupOnStartRun";
  private final Project myProject;

  private String myConfigurationDirectory;
  private boolean myIsUseDefaultConfiguration;
  private boolean myIsUseDefaultProxy;
  private ISVNOptions myOptions;
  private boolean myIsKeepLocks;
  private boolean myAutoUpdateAfterCommit;
  private SvnAuthenticationManager myAuthManager;
  private SvnAuthenticationManager myPassiveAuthManager;
  private SvnAuthenticationManager myInteractiveManager;
  private SvnSupportOptions mySupportOptions;
  private boolean myCleanupRun;
  private int myMaxAnnotateRevisions = ourMaxAnnotateRevisionsDefault;
  private final static long DEFAULT_SSH_TIMEOUT = 30 * 1000;
  public long mySSHConnectionTimeout = DEFAULT_SSH_TIMEOUT;
  public long mySSHReadTimeout = DEFAULT_SSH_TIMEOUT;

  public static final AuthStorage RUNTIME_AUTH_CACHE = new AuthStorage();
  // TODO: update depth is not stored in configuration as SVNDepth has wrong type for DefaultJDOMExternalizer
  // TODO: check if it should be stored
  public SVNDepth UPDATE_DEPTH = SVNDepth.UNKNOWN;

  public boolean MERGE_DRY_RUN = false;
  public boolean MERGE_DIFF_USE_ANCESTRY = true;
  public boolean UPDATE_LOCK_ON_DEMAND = false;
  public boolean IGNORE_SPACES_IN_MERGE = false;
  public boolean CHECK_NESTED_FOR_QUICK_MERGE = false;
  public boolean IGNORE_SPACES_IN_ANNOTATE = true;
  public boolean SHOW_MERGE_SOURCES_IN_ANNOTATE = true;
  public boolean FORCE_UPDATE = false;
  public boolean IGNORE_EXTERNALS = false;
  public Boolean TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE;
  public SSLProtocols SSL_PROTOCOLS = (SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.7") || SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.8")) ?
    SSLProtocols.all : SSLProtocols.sslv3;

  public UseAcceleration myUseAcceleration = UseAcceleration.nothing;

  private final Map<File, MergeRootInfo> myMergeRootInfos = new HashMap<File, MergeRootInfo>();
  private final Map<File, UpdateRootInfo> myUpdateRootInfos = new HashMap<File, UpdateRootInfo>();
  private SvnInteractiveAuthenticationProvider myInteractiveProvider;
  private IdeaSVNConfigFile myConfigFile;

  public boolean isCommandLine() {
    return UseAcceleration.commandLine.equals(getUseAcceleration());
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    try {
      writeExternal(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public long getHttpTimeout() {
    initServers();
    final String timeout = myConfigFile.getDefaultGroup().getTimeout();
    try {
      return Long.parseLong(timeout) * 1000;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public SVNDiffOptions getMergeOptions() {
    return new SVNDiffOptions(isIgnoreSpacesInMerge(), isIgnoreSpacesInMerge(), isIgnoreSpacesInMerge());
  }

  private void initServers() {
    if (myConfigFile == null) {
      myConfigFile = new IdeaSVNConfigFile(new File(getConfigurationDirectory(), IdeaSVNConfigFile.SERVERS_FILE_NAME));
    }
    myConfigFile.updateGroups();
  }

  // uses configuration directory property - it should be saved first
  public void setHttpTimeout(final long value) {
    initServers();
    long cut = value / 1000;
    myConfigFile.setValue("global", SvnServerFileKeys.TIMEOUT, String.valueOf(cut));
    myConfigFile.save();
  }

  public static SvnConfiguration getInstance(final Project project) {
    return ServiceManager.getService(project, SvnConfiguration.class);
  }

  public SvnConfiguration(final Project project) {
    myProject = project;
  }

  public void setIgnoreSpacesInAnnotate(final boolean value) {
    final boolean changed = IGNORE_SPACES_IN_ANNOTATE != value;
    IGNORE_SPACES_IN_ANNOTATE = value;
    if (changed) {
      getProject().getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(SvnVcs.getKey());
    }
  }

  public long getSshConnectionTimeout() {
    return mySSHConnectionTimeout;
  }

  public void setSshConnectionTimeout(long sshConnectionTimeout) {
    mySSHConnectionTimeout = sshConnectionTimeout;
  }

  public long getSshReadTimeout() {
    return mySSHReadTimeout;
  }

  public void setSshReadTimeout(long sshReadTimeout) {
    mySSHReadTimeout = sshReadTimeout;
  }

  public Project getProject() {
    return myProject;
  }

  public Boolean isKeepNewFilesAsIsForTreeConflictMerge() {
    return TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE;
  }

  public void setKeepNewFilesAsIsForTreeConflictMerge(Boolean keepNewFilesAsIsForTreeConflictMerge) {
    this.TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE = keepNewFilesAsIsForTreeConflictMerge;
  }

  public SSLProtocols getSslProtocols() {
    return SSL_PROTOCOLS;
  }

  public void setSslProtocols(SSLProtocols sslProtocols) {
    this.SSL_PROTOCOLS = sslProtocols;
  }

  public SVNDepth getUpdateDepth() {
    return UPDATE_DEPTH;
  }

  public void setUpdateDepth(SVNDepth updateDepth) {
    this.UPDATE_DEPTH = updateDepth;
  }

  public UseAcceleration getUseAcceleration() {
    return myUseAcceleration;
  }

  public void setUseAcceleration(UseAcceleration useAcceleration) {
    myUseAcceleration = useAcceleration;
  }

  public boolean isIgnoreExternals() {
    return IGNORE_EXTERNALS;
  }

  public void setIgnoreExternals(boolean ignoreExternals) {
    this.IGNORE_EXTERNALS = ignoreExternals;
  }

  public boolean isMergeDryRun() {
    return MERGE_DRY_RUN;
  }

  public void setMergeDryRun(boolean mergeDryRun) {
    this.MERGE_DRY_RUN = mergeDryRun;
  }

  public boolean isMergeDiffUseAncestry() {
    return MERGE_DIFF_USE_ANCESTRY;
  }

  public void setMergeDiffUseAncestry(boolean mergeDiffUseAncestry) {
    this.MERGE_DIFF_USE_ANCESTRY = mergeDiffUseAncestry;
  }

  public boolean isUpdateLockOnDemand() {
    return UPDATE_LOCK_ON_DEMAND;
  }

  public void setUpdateLockOnDemand(boolean updateLockOnDemand) {
    this.UPDATE_LOCK_ON_DEMAND = updateLockOnDemand;
  }

  public boolean isIgnoreSpacesInMerge() {
    return IGNORE_SPACES_IN_MERGE;
  }

  public void setIgnoreSpacesInMerge(boolean ignoreSpacesInMerge) {
    this.IGNORE_SPACES_IN_MERGE = ignoreSpacesInMerge;
  }

  public boolean isCheckNestedForQuickMerge() {
    return CHECK_NESTED_FOR_QUICK_MERGE;
  }

  public void setCheckNestedForQuickMerge(boolean checkNestedForQuickMerge) {
    this.CHECK_NESTED_FOR_QUICK_MERGE = checkNestedForQuickMerge;
  }

  public boolean isIgnoreSpacesInAnnotate() {
    return IGNORE_SPACES_IN_ANNOTATE;
  }

  public boolean isShowMergeSourcesInAnnotate() {
    return SHOW_MERGE_SOURCES_IN_ANNOTATE;
  }

  public void setShowMergeSourcesInAnnotate(boolean showMergeSourcesInAnnotate) {
    this.SHOW_MERGE_SOURCES_IN_ANNOTATE = showMergeSourcesInAnnotate;
  }

  public boolean isForceUpdate() {
    return FORCE_UPDATE;
  }

  public void setForceUpdate(boolean forceUpdate) {
    this.FORCE_UPDATE = forceUpdate;
  }

  public class SvnSupportOptions {
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    private Long myVersion;

    public SvnSupportOptions(final Long version) {
      myVersion = version;
      // will be set to SvnSupportOptions.CHANGELIST_SUPPORT after sync
      if (myVersion == null || myVersion.longValue() < SvnSupportOptions.CHANGELIST_SUPPORT) {
        myVersion = SvnSupportOptions.UPGRADE_TO_15_VERSION_ASKED;
      }
    }

    private final static long UPGRADE_TO_15_VERSION_ASKED = 123;
    private final static long CHANGELIST_SUPPORT = 124;
    private final static long UPGRADE_TO_16_VERSION_ASKED = 125;

    public boolean changeListsSynchronized() {
      return (myVersion != null) && (CHANGELIST_SUPPORT <= myVersion);
    }

    public void upgrade() {
      myVersion = UPGRADE_TO_16_VERSION_ASKED;
    }
  }

  public SvnSupportOptions getSupportOptions(Project project) {
    if (mySupportOptions == null) {
      // used to be kept in SvnBranchConfigurationManager
      mySupportOptions = new SvnSupportOptions(SvnBranchConfigurationManager.getInstance(project).getSupportValue());
    }
    return mySupportOptions;
  }

  public String getConfigurationDirectory() {
    if (myConfigurationDirectory == null || isUseDefaultConfiguation()) {
      myConfigurationDirectory = IdeaSubversionConfigurationDirectory.getPath();
    }
    return myConfigurationDirectory;
  }

  public boolean isUseDefaultConfiguation() {
    return myIsUseDefaultConfiguration;
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
    myConfigurationDirectory = path;
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
    myIsUseDefaultConfiguration = useDefault;
  }

  public ISVNOptions getOptions(Project project) {
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
      myAuthManager.setAuthenticationProvider(new SvnAuthenticationProvider(svnVcs, myInteractiveProvider, RUNTIME_AUTH_CACHE));
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
    initServers();
    userManager.set(new SvnServerFileManagerImpl(myConfigFile));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    Element configurationDirectory = element.getChild("configuration");
    if (configurationDirectory != null) {
      myConfigurationDirectory = configurationDirectory.getText();
      Attribute defaultAttr = configurationDirectory.getAttribute("useDefault");
      try {
        myIsUseDefaultConfiguration = defaultAttr != null && defaultAttr.getBooleanValue();
      }
      catch (DataConversionException e) {
        myIsUseDefaultConfiguration = false;
      }
    }
    else {
      myIsUseDefaultConfiguration = true;
    }
    myIsKeepLocks = element.getChild("keepLocks") != null;
    final Element useProxy = element.getChild("myIsUseDefaultProxy");
    if (useProxy == null) {
      myIsUseDefaultProxy = false;
    } else {
      myIsUseDefaultProxy = Boolean.parseBoolean(useProxy.getText());
    }
    final Element supportedVersion = element.getChild("supportedVersion");
    if (supportedVersion != null) {
      try {
        mySupportOptions = new SvnSupportOptions(Long.parseLong(supportedVersion.getText().trim()));
      } catch (NumberFormatException e) {
        mySupportOptions = new SvnSupportOptions(null);
      }
    }
    final Attribute maxAnnotateRevisions = element.getAttribute("maxAnnotateRevisions");
    if (maxAnnotateRevisions != null) {
      try {
        myMaxAnnotateRevisions = maxAnnotateRevisions.getIntValue();
      }
      catch (DataConversionException e) {
        //
      }
      final Attribute acceleration = element.getAttribute("myUseAcceleration");
      if (acceleration != null) {
        try {
          setUseAcceleration(UseAcceleration.valueOf(acceleration.getValue()));
        } catch (IllegalArgumentException e) {
          //
        }
      }
    }
    final Attribute autoUpdateAfterCommit = element.getAttribute("myAutoUpdateAfterCommit");
    if (autoUpdateAfterCommit != null) {
      myAutoUpdateAfterCommit = Boolean.parseBoolean(autoUpdateAfterCommit.getValue());
    }
    final Attribute cleanupRun = element.getAttribute(CLEANUP_ON_START_RUN);
    if (cleanupRun != null) {
      myCleanupRun = Boolean.parseBoolean(cleanupRun.getValue());
    }
    final Attribute treeConflictMergeNewFilesPlace = element.getAttribute("TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE");
    final Attribute protocols = element.getAttribute("SSL_PROTOCOLS");
    if (protocols != null) {
      try {
        setSslProtocols(SSLProtocols.valueOf(protocols.getValue()));
      } catch (IllegalArgumentException e) {
        //
      }
    }
    if (treeConflictMergeNewFilesPlace != null) {
      setKeepNewFilesAsIsForTreeConflictMerge(Boolean.parseBoolean(treeConflictMergeNewFilesPlace.getValue()));
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    if (myConfigurationDirectory != null) {
      Element configurationDirectory = new Element("configuration");
      configurationDirectory.setText(myConfigurationDirectory);
      configurationDirectory.setAttribute("useDefault", myIsUseDefaultConfiguration ? "true" : "false");
      element.addContent(configurationDirectory);
    }
    if (myIsKeepLocks) {
      element.addContent(new Element("keepLocks"));
    }
    element.addContent(new Element("myIsUseDefaultProxy").setText(myIsUseDefaultProxy ? "true" : "false"));
    if (mySupportOptions != null) {
      element.addContent(new Element("supportedVersion").setText(String.valueOf(mySupportOptions.myVersion)));
    }
    element.setAttribute("maxAnnotateRevisions", String.valueOf(myMaxAnnotateRevisions));
    element.setAttribute("myUseAcceleration", String.valueOf(getUseAcceleration()));
    element.setAttribute("myAutoUpdateAfterCommit", String.valueOf(myAutoUpdateAfterCommit));
    element.setAttribute(CLEANUP_ON_START_RUN, String.valueOf(myCleanupRun));
    element.setAttribute("SSL_PROTOCOLS", getSslProtocols().name());
    if (isKeepNewFilesAsIsForTreeConflictMerge() != null) {
      element.setAttribute("TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE", String.valueOf(
        isKeepNewFilesAsIsForTreeConflictMerge()));
    }
  }

  public boolean isAutoUpdateAfterCommit() {
    return myAutoUpdateAfterCommit;
  }

  public void setAutoUpdateAfterCommit(boolean autoUpdateAfterCommit) {
    myAutoUpdateAfterCommit = autoUpdateAfterCommit;
  }

  public boolean isKeepLocks() {
    return myIsKeepLocks;
  }

  public void setKeepLocks(boolean keepLocks) {
    myIsKeepLocks = keepLocks;
  }

  public boolean isIsUseDefaultProxy() {
    return myIsUseDefaultProxy;
  }

  public void setIsUseDefaultProxy(final boolean isUseDefaultProxy) {
    myIsUseDefaultProxy = isUseDefaultProxy;
  }

  // TODO: Rewrite AutoStorage to use MemoryPasswordSafe at least
  public static class AuthStorage implements ISVNAuthenticationStorage {

    private final Map<String, Object> myStorage = Collections.synchronizedMap(new HashMap<String, Object>());

    public void clear() {
      myStorage.clear();
    }

    public void putData(String kind, String realm, Object data) {
      if (data == null) {
        myStorage.remove(kind + "$" + realm);
      } else {
        myStorage.put(kind + "$" + realm, data);
      }
    }

    public Object getData(String kind, String realm) {
      return myStorage.get(kind + "$" + realm);
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
    return myMaxAnnotateRevisions;
  }

  public void setMaxAnnotateRevisions(int maxAnnotateRevisions) {
    myMaxAnnotateRevisions = maxAnnotateRevisions;
  }

  public enum UseAcceleration {
    commandLine,
    nothing
  }

  public boolean isCleanupRun() {
    return myCleanupRun;
  }

  public void setCleanupRun(boolean cleanupRun) {
    myCleanupRun = cleanupRun;
  }

  public enum SSLProtocols {
    sslv3, tlsv1, all
  }
}
