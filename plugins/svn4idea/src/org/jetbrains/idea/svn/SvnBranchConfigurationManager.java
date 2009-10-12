/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private final Project myProject;

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
    public boolean mySupportsUserInfoFilter;
  }

  public Long getSupportValue() {
    return myConfigurationBean.myVersion;
  }

  private ConfigurationBean myConfigurationBean = new ConfigurationBean();

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  public SvnBranchConfiguration get(@NotNull final VirtualFile vcsRoot) throws VcsException {
    SvnBranchConfiguration configuration = myConfigurationBean.myConfigurationMap.get(vcsRoot.getPath());
    if (configuration == null) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        final Ref<VcsException> exceptionRef = new Ref<VcsException>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            try {
              ProgressManager.getInstance().getProgressIndicator().setText(
                SvnBundle.message("loading.data.for.root.text", vcsRoot.getPresentableUrl()));
              final SvnBranchConfiguration loadedConfiguration = load(vcsRoot);
              setConfiguration(vcsRoot, loadedConfiguration, false);
            }
            catch (VcsException e) {
              exceptionRef.set(e);
            }
          }
        }, SvnBundle.message("loading.default.branches.configuration.text"), false, myProject);
        if (! exceptionRef.isNull()) {
          // set empty configuration... to do not repeat forever when invoked in cycle
          setConfiguration(vcsRoot, new SvnBranchConfiguration(), true);
          throw exceptionRef.get();
        }
        configuration = myConfigurationBean.myConfigurationMap.get(vcsRoot.getPath());
      } else {
        configuration = load(vcsRoot);
        setConfiguration(vcsRoot, configuration, false);
      }
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
    final ConfigurationBean result = new ConfigurationBean();
    result.myVersion = myConfigurationBean.myVersion;
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));
    for (Map.Entry<String, SvnBranchConfiguration> entry : myConfigurationBean.myConfigurationMap.entrySet()) {
      final SvnBranchConfiguration configuration = entry.getValue();
      if ((! myConfigurationBean.mySupportsUserInfoFilter) || configuration.isUserinfoInUrl()) {
        result.myConfigurationMap.put(entry.getKey(), helper.prepareForSerialization(configuration));
      } else {
        result.myConfigurationMap.put(entry.getKey(), entry.getValue());
      }
    }
    result.mySupportsUserInfoFilter = true;
    return result;
  }

  public void loadState(final ConfigurationBean object) {
    final UrlSerializationHelper helper = new UrlSerializationHelper(SvnVcs.getInstance(myProject));
    final Map<String, SvnBranchConfiguration> map = object.myConfigurationMap;
    final Map<String, SvnBranchConfiguration> newMap = new HashMap<String, SvnBranchConfiguration>(map.size(), 1);
    for (Map.Entry<String, SvnBranchConfiguration> entry : map.entrySet()) {
      final SvnBranchConfiguration configuration = entry.getValue();
      if ((! myConfigurationBean.mySupportsUserInfoFilter) || configuration.isUserinfoInUrl()) {
        newMap.put(entry.getKey(), helper.afterDeserialization(entry.getKey(), configuration));
      } else {
        newMap.put(entry.getKey(), entry.getValue());
      }
    }
    object.myConfigurationMap.clear();
    object.myConfigurationMap.putAll(newMap);
    myConfigurationBean = object;
  }

  private static class UrlSerializationHelper {
    private final SvnVcs myVcs;

    private UrlSerializationHelper(final SvnVcs vcs) {
      myVcs = vcs;
    }

    public SvnBranchConfiguration prepareForSerialization(final SvnBranchConfiguration configuration) {
      final Ref<Boolean> withUserInfo = new Ref<Boolean>();
      final String trunkUrl = serializeUrl(configuration.getTrunkUrl(), withUserInfo);

      if (Boolean.FALSE.equals(withUserInfo.get())) {
        return configuration;
      }

      final List<String> branches = configuration.getBranchUrls();
      final List<String> newBranchesList = new ArrayList<String>(branches.size());
      for (String s : branches) {
        newBranchesList.add(serializeUrl(s, withUserInfo));
      }

      final Map<String, List<SvnBranchItem>> map = configuration.getBranchMap();
      final Map<String, List<SvnBranchItem>> newMap = new HashMap<String, List<SvnBranchItem>>(map.size(), 1.0f);
      for (Map.Entry<String, List<SvnBranchItem>> entry : map.entrySet()) {
        final List<SvnBranchItem> items = entry.getValue();
        if (items != null) {
          final List<SvnBranchItem> newItems = new ArrayList<SvnBranchItem>();
          for (SvnBranchItem item : items) {
            newItems.add(new SvnBranchItem(serializeUrl(item.getUrl(), withUserInfo), new java.util.Date(item.getCreationDateMillis()),
                                           item.getRevision()));
          }
          newMap.put(serializeUrl(entry.getKey(), withUserInfo), newItems);
        }
      }

      final SvnBranchConfiguration result = new SvnBranchConfiguration();
      result.setTrunkUrl(trunkUrl);
      result.setBranchUrls(newBranchesList);
      result.setBranchMap(newMap);
      result.setUserinfoInUrl(withUserInfo.isNull() ? false : withUserInfo.get());
      return result;
    }

    public SvnBranchConfiguration afterDeserialization(final String path, final SvnBranchConfiguration configuration) {
      if (! configuration.isUserinfoInUrl()) {
        return configuration;
      }
      final String userInfo = getUserInfo(path);
      if (userInfo == null) {
        return configuration;
      }

      final String newTrunkUrl = deserializeUrl(configuration.getTrunkUrl(), userInfo);
      final List<String> branches = configuration.getBranchUrls();
      final List<String> newBranchesList = new ArrayList<String>(branches.size());
      for (String s : branches) {
        newBranchesList.add(deserializeUrl(s, userInfo));
      }

      final Map<String, List<SvnBranchItem>> map = configuration.getBranchMap();
      final Map<String, List<SvnBranchItem>> newMap = new HashMap<String, List<SvnBranchItem>>(map.size(), 1.0f);
      for (Map.Entry<String, List<SvnBranchItem>> entry : map.entrySet()) {
        final List<SvnBranchItem> items = entry.getValue();
        if (items != null) {
          final List<SvnBranchItem> newItems = new ArrayList<SvnBranchItem>();
          for (SvnBranchItem item : items) {
            newItems.add(new SvnBranchItem(deserializeUrl(item.getUrl(), userInfo), new java.util.Date(item.getCreationDateMillis()),
                                           item.getRevision()));
          }
          newMap.put(deserializeUrl(entry.getKey(), userInfo), newItems);
        }
      }

      final SvnBranchConfiguration result = new SvnBranchConfiguration();
      result.setTrunkUrl(newTrunkUrl);
      result.setBranchUrls(newBranchesList);
      result.setBranchMap(newMap);
      result.setUserinfoInUrl(userInfo != null && userInfo.length() > 0);
      return result;
    }

    private String serializeUrl(final String url, final Ref<Boolean> withUserInfo) {
      if (Boolean.FALSE.equals(withUserInfo.get())) {
        return url;
      }
      try {
        final SVNURL svnurl = SVNURL.parseURIEncoded(url);
        if (withUserInfo.isNull()) {
          final String userInfo = svnurl.getUserInfo();
          withUserInfo.set((userInfo != null) && (userInfo.length() > 0));
        }
        if (withUserInfo.get()) {
          return SVNURL.create(svnurl.getProtocol(), null, svnurl.getHost(), svnurl.getPort(), svnurl.getURIEncodedPath(), true).toString();
        }
      }
      catch (SVNException e) {
        //
      }
      return url;
    }

    @Nullable
    private String getUserInfo(final String path) {
      final SVNURL svnurl = myVcs.getSvnFileUrlMapping().getUrlForFile(new File(path));
      return svnurl != null ? svnurl.getUserInfo() : null;
    }

    private String deserializeUrl(final String url, final String userInfo) {
      try {
        final SVNURL svnurl = SVNURL.parseURIEncoded(url);
        return SVNURL.create(svnurl.getProtocol(), userInfo, svnurl.getHost(), svnurl.getPort(), svnurl.getURIEncodedPath(), true).toString();
      } catch (SVNException e) {
        return url;
      }
    }
  }
}
