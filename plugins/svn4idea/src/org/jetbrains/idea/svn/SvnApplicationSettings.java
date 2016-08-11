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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@State(
  name="SvnApplicationSettings",
  storages = {
    @Storage("other.xml")}
)
public class SvnApplicationSettings implements PersistentStateComponent<SvnApplicationSettings.ConfigurationBean> {
  private SvnFileSystemListener myVFSHandler;
  private int mySvnProjectCount;
  private LimitedStringsList myLimitedStringsList;

  public static class ConfigurationBean {
    public List<String> myCheckoutURLs = new ArrayList<>();
    public List<String> myTypedURLs = new ArrayList<>();
    public String mySvnCommandLine = "svn";
  }

  private ConfigurationBean myConfigurationBean;

  public static SvnApplicationSettings getInstance() {
    return ServiceManager.getService(SvnApplicationSettings.class);
  }

  public SvnApplicationSettings() {
    myConfigurationBean = new ConfigurationBean();
  }

  public ConfigurationBean getState() {
    myConfigurationBean.myTypedURLs.clear();
    myConfigurationBean.myTypedURLs.addAll(getTypedList().getList());
    return myConfigurationBean;
  }

  public void loadState(ConfigurationBean object) {
    myConfigurationBean = object;
    getTypedList();
  }

  public void setCommandLinePath(final String path) {
    myConfigurationBean.mySvnCommandLine = path;
  }

  public String getCommandLinePath() {
    return myConfigurationBean.mySvnCommandLine;
  }

  private LimitedStringsList getTypedList() {
    if (myLimitedStringsList == null) {
      checkFillTypedFromCheckout();
      myLimitedStringsList = new LimitedStringsList(myConfigurationBean.myTypedURLs);
    }
    return myLimitedStringsList;
  }

  private void checkFillTypedFromCheckout() {
    if (myConfigurationBean.myTypedURLs.isEmpty() && (! myConfigurationBean.myCheckoutURLs.isEmpty())) {
      myConfigurationBean.myTypedURLs.addAll(myConfigurationBean.myCheckoutURLs);
    }
  }

  public void svnActivated() {
    if (myVFSHandler == null) {
      myVFSHandler = new SvnFileSystemListener();
    }
    mySvnProjectCount++;
  }

  public void svnDeactivated() {
    mySvnProjectCount--;
    if (mySvnProjectCount == 0) {
      Disposer.dispose(myVFSHandler);
      myVFSHandler = null;
      // todo what should be done instead?
      //SVNSSHSession.shutdown();
    }
  }

  private static File getCommonPath() {
    File file = new File(PathManager.getSystemPath());
    file = new File(file, "plugins");
    file = new File(file, "svn4idea");
    file.mkdirs();
    return file;
  }

  public static File getCredentialsFile() {
    return new File(getCommonPath(), "credentials.xml");
  }

  private static final String LOADED_REVISIONS_DIR = "loadedRevisions";

  public static File getLoadedRevisionsDir(final Project project) {
    File file = getCommonPath();
    file = new File(file, LOADED_REVISIONS_DIR);
    file = new File(file, project.getLocationHash());
    file.mkdirs();
    return file;
  }

  public Collection<String> getCheckoutURLs() {
    return myConfigurationBean.myCheckoutURLs;
  }

  public void addCheckoutURL(String url) {
    if (myConfigurationBean.myCheckoutURLs.contains(url)) {
      return;
    }
    myConfigurationBean.myCheckoutURLs.add(0, url);
  }

  public void removeCheckoutURL(String url) {
    if (myConfigurationBean.myCheckoutURLs != null) {
      // 'url' is not necessary an exact match for some of the urls in collection - it has been parsed and then converted back to string
      for(String oldUrl: myConfigurationBean.myCheckoutURLs) {
        try {
          if (url.equals(oldUrl) || SVNURL.parseURIEncoded(url).equals(SVNURL.parseURIEncoded(oldUrl))) {
            myConfigurationBean.myCheckoutURLs.remove(oldUrl);
            break;
          }
        }
        catch (SVNException e) {
          // ignore
        }
      }
    }
  }

  public List<String> getTypedUrlsListCopy() {
    return new ArrayList<>(getTypedList().getList());
  }

  public void addTypedUrl(final String url) {
    getTypedList().add(url);
  }
}
