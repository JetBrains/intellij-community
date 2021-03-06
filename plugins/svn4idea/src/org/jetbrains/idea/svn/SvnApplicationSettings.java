// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;


@State(
  name="SvnApplicationSettings",
  storages = {
    @Storage("other.xml")}
)
public class SvnApplicationSettings implements PersistentStateComponent<SvnApplicationSettings.ConfigurationBean> {
  private LimitedStringsList myLimitedStringsList;

  public static class ConfigurationBean {
    public List<String> myCheckoutURLs = new ArrayList<>();
    public List<String> myTypedURLs = new ArrayList<>();
    public @NlsSafe String mySvnCommandLine = "svn";
  }

  private ConfigurationBean myConfigurationBean;

  public static SvnApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(SvnApplicationSettings.class);
  }

  public SvnApplicationSettings() {
    myConfigurationBean = new ConfigurationBean();
  }

  @Override
  public ConfigurationBean getState() {
    myConfigurationBean.myTypedURLs.clear();
    myConfigurationBean.myTypedURLs.addAll(getTypedList().getList());
    return myConfigurationBean;
  }

  @Override
  public void loadState(@NotNull ConfigurationBean object) {
    myConfigurationBean = object;
    getTypedList();
  }

  public void setCommandLinePath(@NlsSafe String path) {
    myConfigurationBean.mySvnCommandLine = path;
  }

  public @NlsSafe String getCommandLinePath() {
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
          if (url.equals(oldUrl) || createUrl(url).equals(createUrl(oldUrl))) {
            myConfigurationBean.myCheckoutURLs.remove(oldUrl);
            break;
          }
        }
        catch (SvnBindException ignored) {
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
