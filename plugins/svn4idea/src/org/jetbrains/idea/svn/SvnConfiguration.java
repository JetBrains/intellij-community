/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.dialogs.SvnInteractiveAuthenticationProvider;
import org.jetbrains.idea.svn.update.MergeRootInfo;
import org.jetbrains.idea.svn.update.UpdateRootInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

@State(
  name = "SvnConfiguration",
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )
  }
)
public class SvnConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnConfiguration");
  public final static int ourMaxAnnotateRevisionsDefault = 500;

  private final static String SERVERS_FILE_NAME = "servers";
  
  public static final String UPGRADE_AUTO = "auto";
  public static final String UPGRADE_AUTO_15 = "auto1.5";
  public static final String UPGRADE_AUTO_16 = "auto1.6";
  public static final String UPGRADE_AUTO_17 = "auto1.7";
  public static final String UPGRADE_NONE = "none";
  public static final String CLEANUP_ON_START_RUN = "cleanupOnStartRun";
  private final Project myProject;

  public String USER = "";
  public String PASSWORD = "";
  public String[] ADD_PATHS = null;

  private String myConfigurationDirectory;
  private boolean myIsUseDefaultConfiguration;
  private boolean myIsUseDefaultProxy;
  private ISVNOptions myOptions;
  private boolean myIsKeepLocks;
  private boolean myAutoUpdateAfterCommit;
  private boolean myRemoteStatus;
  private SvnAuthenticationManager myAuthManager;
  private SvnAuthenticationManager myPassiveAuthManager;
  private SvnAuthenticationManager myInteractiveManager;
  private String myUpgradeMode;
  private SvnSupportOptions mySupportOptions;
  private boolean myCleanupRun;
  private int myMaxAnnotateRevisions = ourMaxAnnotateRevisionsDefault;
  private final static long DEFAULT_SSH_TIMEOUT = 30 * 1000;
  public long mySSHConnectionTimeout = DEFAULT_SSH_TIMEOUT;
  public long mySSHReadTimeout = DEFAULT_SSH_TIMEOUT;

  public static final AuthStorage RUNTIME_AUTH_CACHE = new AuthStorage();
  public String LAST_MERGED_REVISION = null;
  public SVNDepth UPDATE_DEPTH = SVNDepth.UNKNOWN;

  public boolean MERGE_DRY_RUN = false;
  public boolean MERGE_DIFF_USE_ANCESTRY = true;
  public boolean UPDATE_LOCK_ON_DEMAND = false;
  public boolean IGNORE_SPACES_IN_MERGE = false;
  public boolean DETECT_NESTED_COPIES = true;
  public boolean CHECK_NESTED_FOR_QUICK_MERGE = false;
  public boolean IGNORE_SPACES_IN_ANNOTATE = true;
  public boolean SHOW_MERGE_SOURCES_IN_ANNOTATE = true;
  public boolean FORCE_UPDATE = false;
  public boolean IGNORE_EXTERNALS = false;
  public Boolean TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE;

  public UseAcceleration myUseAcceleration = UseAcceleration.nothing;

  private final Map<File, MergeRootInfo> myMergeRootInfos = new HashMap<File, MergeRootInfo>();
  private final Map<File, UpdateRootInfo> myUpdateRootInfos = new HashMap<File, UpdateRootInfo>();
  private SvnInteractiveAuthenticationProvider myInteractiveProvider;

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
      myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(SvnVcs.getKey());
    }
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

    public boolean upgradeTo16Asked() {
      return (myVersion != null) && (UPGRADE_TO_16_VERSION_ASKED <= myVersion);
    }

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

  public void setConfigurationDirectory(String path) {
    myConfigurationDirectory = path;
    File dir = path == null ? new File(IdeaSubversionConfigurationDirectory.getPath()) : new File(path);
    SVNConfigFile.createDefaultConfiguration(dir);

    clear();
  }

  public void clear() {
    myOptions = null;
    myAuthManager = null;
    myPassiveAuthManager = null;
    myInteractiveManager = null;
    myInteractiveProvider = null;
    RUNTIME_AUTH_CACHE.clear();
  }

  public void setUseDefaultConfiguation(boolean useDefault) {
    myIsUseDefaultConfiguration = useDefault;
    clear();
  }

  public ISVNOptions getOptions(Project project) {
    if (myOptions == null) {
      File path = new File(getConfigurationDirectory());
      myOptions = SVNWCUtil.createDefaultOptions(path.getAbsoluteFile(), true);
    }
    return myOptions;
  }

  public static SvnAuthenticationManager createForTmpDir(final Project project, final File dir) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    //final SvnAuthenticationManager manager = new SvnAuthenticationManager(project, dir);

    final SvnAuthenticationManager interactive = new SvnAuthenticationManager(project, dir);
    interactive.setRuntimeStorage(RUNTIME_AUTH_CACHE);
    final SvnInteractiveAuthenticationProvider interactiveProvider = new SvnInteractiveAuthenticationProvider(vcs, interactive);
    interactive.setAuthenticationProvider(interactiveProvider);

    //manager.setAuthenticationProvider(new SvnAuthenticationProvider(vcs, interactiveProvider, RUNTIME_AUTH_CACHE));
    //manager.setRuntimeStorage(RUNTIME_AUTH_CACHE);
    return interactive;
  }

  public SvnAuthenticationManager getAuthenticationManager(final SvnVcs svnVcs) {
    if (myAuthManager == null) {
      // reloaded when configuration directory changes
        myAuthManager = new SvnAuthenticationManager(svnVcs.getProject(), new File(getConfigurationDirectory()));
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
    SVNConfigFile.createDefaultConfiguration(new File(getConfigurationDirectory()));

    systemManager.set(new SvnServerFileManagerImpl(new IdeaSVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), SERVERS_FILE_NAME))));
    userManager.set(new SvnServerFileManagerImpl(new IdeaSVNConfigFile(new File(getConfigurationDirectory(), SERVERS_FILE_NAME))));
  }

  public String getUpgradeMode() {
    return myUpgradeMode;
  }

  public void setUpgradeMode(String upgradeMode) {
    myUpgradeMode = upgradeMode;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    List elems = element.getChildren("addpath");
    LOG.debug(elems.toString());
    ADD_PATHS = new String[elems.size()];
    for (int i = 0; i < elems.size(); i++) {
      Element elem = (Element)elems.get(i);
      ADD_PATHS[i] = elem.getAttributeValue("path");
    }
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
    // compatibility: this setting was moved from .iws to global settings
    List urls = element.getChildren("checkoutURL");
    for (Object url1 : urls) {
      Element child = (Element)url1;
      String url = child.getText();
      if (url != null) {
        SvnApplicationSettings.getInstance().addCheckoutURL(url);
      }
    }
    myIsKeepLocks = element.getChild("keepLocks") != null;
    myRemoteStatus = element.getChild("remoteStatus") != null;
    myUpgradeMode = element.getChild("upgradeMode") != null ? element.getChild("upgradeMode").getText() : null;
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
          myUseAcceleration = UseAcceleration.valueOf(acceleration.getValue());
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
    if (treeConflictMergeNewFilesPlace != null) {
      TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE = Boolean.parseBoolean(treeConflictMergeNewFilesPlace.getValue());
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    if (ADD_PATHS != null) {
      for (String aADD_PATHS : ADD_PATHS) {
        Element elem = new Element("addpath");
        elem.setAttribute("path", aADD_PATHS);
        element.addContent(elem);
      }
    }
    if (myConfigurationDirectory != null) {
      Element configurationDirectory = new Element("configuration");
      configurationDirectory.setText(myConfigurationDirectory);
      configurationDirectory.setAttribute("useDefault", myIsUseDefaultConfiguration ? "true" : "false");
      element.addContent(configurationDirectory);
    }
    if (myIsKeepLocks) {
      element.addContent(new Element("keepLocks"));
    }
    if (myRemoteStatus) {
      element.addContent(new Element("remoteStatus"));
    }
    if (myUpgradeMode != null) {
      element.addContent(new Element("upgradeMode").setText(myUpgradeMode));
    }
    element.addContent(new Element("myIsUseDefaultProxy").setText(myIsUseDefaultProxy ? "true" : "false"));
    if (mySupportOptions != null) {
      element.addContent(new Element("supportedVersion").setText("" + mySupportOptions.myVersion));
    }
    element.setAttribute("maxAnnotateRevisions", "" + myMaxAnnotateRevisions);
    element.setAttribute("myUseAcceleration", "" + myUseAcceleration);
    element.setAttribute("myAutoUpdateAfterCommit", "" + myAutoUpdateAfterCommit);
    element.setAttribute(CLEANUP_ON_START_RUN, "" + myCleanupRun);
    if (TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE != null) {
      element.setAttribute("TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE", "" + TREE_CONFLICT_MERGE_THEIRS_NEW_INTO_OLD_PLACE);
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

  public boolean isRemoteStatus() {
    return myRemoteStatus;
  }

  public void setRemoteStatus(boolean remote) {
    myRemoteStatus = remote;
  }

  public boolean isIsUseDefaultProxy() {
    return myIsUseDefaultProxy;
  }

  public void setIsUseDefaultProxy(final boolean isUseDefaultProxy) {
    myIsUseDefaultProxy = isUseDefaultProxy;
  }

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

  public Map<File, UpdateRootInfo> getUpdateInfosMap() {
    return Collections.unmodifiableMap(myUpdateRootInfos);
  }

  private static final List<String> ourAuthKinds = Arrays.asList(ISVNAuthenticationManager.PASSWORD, ISVNAuthenticationManager.SSH,
    ISVNAuthenticationManager.SSL, ISVNAuthenticationManager.USERNAME, "svn.ssl.server", "svn.ssh.server");

  public void clearAuthenticationDirectory(@Nullable Project project) {
    final File authDir = new File(getConfigurationDirectory(), "auth");
    if (authDir.exists()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
          if (ind != null) {
            ind.setIndeterminate(true);
            ind.setText("Clearing stored credentials in " + authDir.getAbsolutePath());
          }
          final File[] files = authDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return ourAuthKinds.contains(name);
            }
          });

          for (File dir : files) {
            if (ind != null) {
              ind.setText("Deleting " + dir.getAbsolutePath());
            }
            FileUtil.delete(dir);
          }
        }
      }, "button.text.clear.authentication.cache", false, project);
    }
  }
  
  public boolean haveCredentialsFor(final String kind, final String realm) {
    return RUNTIME_AUTH_CACHE.getData(kind, realm) != null;
  }

  public void acknowledge(final String kind, final String realm, final Object object) {
    RUNTIME_AUTH_CACHE.putData(kind, realm, object);
  }

  public void clearCredentials(final String kind, final String realm) {
    RUNTIME_AUTH_CACHE.putData(kind, realm, null);
  }

  public int getMaxAnnotateRevisions() {
    return myMaxAnnotateRevisions;
  }

  public void setMaxAnnotateRevisions(int maxAnnotateRevisions) {
    myMaxAnnotateRevisions = maxAnnotateRevisions;
  }

  public enum UseAcceleration {
    javaHL,
    commandLine,
    nothing
  }

  public boolean isCleanupRun() {
    return myCleanupRun;
  }

  public void setCleanupRun(boolean cleanupRun) {
    myCleanupRun = cleanupRun;
  }
}
