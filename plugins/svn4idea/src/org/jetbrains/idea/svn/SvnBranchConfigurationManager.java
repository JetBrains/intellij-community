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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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

  public static class ConfigurationBean {
    public Map<String, SvnBranchConfiguration> myConfigurationMap = new HashMap<String, SvnBranchConfiguration>();
  }

  private ConfigurationBean myConfigurationBean = new ConfigurationBean();

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  public SvnBranchConfiguration get(@NotNull VirtualFile vcsRoot) throws VcsException {
    SvnBranchConfiguration configuration = myConfigurationBean.myConfigurationMap.get(vcsRoot.getPath());
    if (configuration == null) {
      configuration = load(vcsRoot);

      setConfiguration(vcsRoot, configuration);
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

  public void setConfiguration(VirtualFile vcsRoot, SvnBranchConfiguration configuration) {
    myConfigurationBean.myConfigurationMap.put(vcsRoot.getPath(), configuration);
    
    SvnBranchMapperManager.getInstance().notifyMappingChanged(myProject, vcsRoot, configuration);
  }

  public ConfigurationBean getState() {
    return myConfigurationBean;
  }

  public void loadState(final ConfigurationBean object) {
    myConfigurationBean = object;
  }
}