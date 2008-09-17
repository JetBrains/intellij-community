/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
@State(
  name = "SvnBranchConfigurationManager",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )}
)
public class SvnBranchConfigurationManager implements PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private Project myProject;

  public SvnBranchConfigurationManager(final Project project) {
    myProject = project;
  }

  public static SvnBranchConfigurationManager getInstance(Project project) {
    return ServiceManager.getService(project, SvnBranchConfigurationManager.class);
  }

  /**
   * Gets the instance of the component if the project wasn't disposed. If the project was
   * disposed, throws ProcessCanceledException. Should only be used for calling from background
   * threads (for example, committed changes refresh thread).
   *
   * @param project the project for which the component instance should be retrieved.
   * @return component instance
   */
  public static SvnBranchConfigurationManager getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<SvnBranchConfigurationManager>() {
      public SvnBranchConfigurationManager compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return getInstance(project);
      }
    });
  }

  public static class ConfigurationBean {
    public Map<String, SvnBranchConfiguration> myConfigurationMap = new HashMap<String, SvnBranchConfiguration>();
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    public Long myVersion;
  }

  public class SvnSupportOptions {
    private final Long myVersion;

    public SvnSupportOptions(final Long version) {
      myVersion = version;
    }

    private final static long UPGRADE_TO_15_VERSION_ASKED = 123;
    private final static long CHANGELIST_SUPPORT = 124;

    public boolean upgradeTo15Asked() {
      return (myVersion != null) && (UPGRADE_TO_15_VERSION_ASKED <= myVersion);
    }

    public boolean changeListsSynchronized() {
      return (myVersion != null) && (CHANGELIST_SUPPORT <= myVersion);
    }

    public void upgradeToChangeListsSynchronized() {
      myConfigurationBean.myVersion = CHANGELIST_SUPPORT;
    }
  }

  public SvnSupportOptions getSupportOptions() {
    final SvnSupportOptions supportOptions = new SvnSupportOptions(myConfigurationBean.myVersion);
    // will be set to SvnSupportOptions.CHANGELIST_SUPPORT after sync
    if (myConfigurationBean.myVersion == null || myConfigurationBean.myVersion.longValue() < SvnSupportOptions.CHANGELIST_SUPPORT) {
      myConfigurationBean.myVersion = SvnSupportOptions.UPGRADE_TO_15_VERSION_ASKED;
    }
    return supportOptions;
  }

  private ConfigurationBean myConfigurationBean = new ConfigurationBean();

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  public SvnBranchConfiguration get(@NotNull VirtualFile vcsRoot) throws VcsException {
    SvnBranchConfiguration configuration = myConfigurationBean.myConfigurationMap.get(vcsRoot.getPath());
    if (configuration == null) {
      configuration = load(vcsRoot);

      setConfiguration(vcsRoot, configuration, false);
    }
    return configuration;
  }

  private SvnBranchConfiguration load(VirtualFile vcsRoot) throws VcsException {
    try {
      SVNURL baseUrl = null;
      final SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      File rootFile = new File(vcsRoot.getPath());
      wcAccess.open(rootFile, false, 0);
      try {
        SVNEntry entry = wcAccess.getEntry(rootFile, false);
        if (entry != null) {
          baseUrl = entry.getSVNURL();
        }
        else {
          throw new VcsException("Directory is not a working copy: " + vcsRoot.getPresentableUrl());
        }
      }
      finally {
        wcAccess.close();
      }

      final SvnBranchConfiguration result = new SvnBranchConfiguration();
      result.setTrunkUrl(baseUrl.toString());
      while(true) {
        final String s = SVNPathUtil.tail(baseUrl.getPath());
        if (s.equalsIgnoreCase(DEFAULT_TRUNK_NAME) || s.equalsIgnoreCase(DEFAULT_BRANCHES_NAME) || s.equalsIgnoreCase(DEFAULT_TAGS_NAME)) {
          final SVNURL rootPath = baseUrl.removePathTail();
          SVNLogClient client = SvnVcs.getInstance(myProject).createLogClient();
          client.doList(rootPath, SVNRevision.UNDEFINED, SVNRevision.HEAD, false, new ISVNDirEntryHandler() {
            public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
              if (("".equals(dirEntry.getRelativePath())) || (! SVNNodeKind.DIR.equals(dirEntry.getKind()))) {
                // do not use itself or files
                return;
              }

              if (dirEntry.getName().toLowerCase().endsWith(DEFAULT_TRUNK_NAME)) {
                result.setTrunkUrl(rootPath.appendPath(dirEntry.getName(), false).toString());
              }
              else {
                result.getBranchUrls().add(rootPath.appendPath(dirEntry.getName(), false).toString());
              }
            }
          });

          break;
        }
        if (SVNPathUtil.removeTail(baseUrl.getPath()).length() == 0) {
          break;
        }
        baseUrl = baseUrl.removePathTail();
      }
      return result;
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  public void setConfiguration(VirtualFile vcsRoot, SvnBranchConfiguration configuration, final boolean underProgress) {
    final String key = vcsRoot.getPath();
    final SvnBranchConfiguration oldConfiguration = myConfigurationBean.myConfigurationMap.get(key);
    if ((oldConfiguration == null) || (oldConfiguration.getBranchMap().isEmpty()) || (oldConfiguration.urlsMissing(configuration))) {
      configuration.loadBranches(myProject, underProgress);
    } else {
      configuration.setBranchMap(oldConfiguration.getBranchMap());
    }

    myConfigurationBean.myConfigurationMap.put(key, configuration);
    SvnBranchMapperManager.getInstance().notifyBranchesChanged(myProject, vcsRoot, configuration);

    final MessageBus messageBus = myProject.getMessageBus();
    messageBus.syncPublisher(VcsConfigurationChangeListener.BRANCHES_CHANGED).execute(myProject, vcsRoot);
  }

  public ConfigurationBean getState() {
    // will be set to SvnSupportOptions.CHANGELIST_SUPPORT after sync
    if (myConfigurationBean.myVersion == null || myConfigurationBean.myVersion.longValue() < SvnSupportOptions.CHANGELIST_SUPPORT) {
      myConfigurationBean.myVersion = SvnSupportOptions.UPGRADE_TO_15_VERSION_ASKED;
    }
    return myConfigurationBean;
  }

  public void loadState(final ConfigurationBean object) {
    myConfigurationBean = object;
  }
}