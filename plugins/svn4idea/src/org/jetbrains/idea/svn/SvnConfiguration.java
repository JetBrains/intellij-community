/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.idea.svn.dialogs.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.update.MergeRootInfo;
import org.jetbrains.idea.svn.update.UpdateRootInfo;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.*;

public class SvnConfiguration implements ProjectComponent, JDOMExternalizable{
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.svn.SvnConfiguration");

  public String USER = "";
  public String PASSWORD = "";
  public String[] ADD_PATHS = null;

  private String myConfigurationDirectory;
  private boolean myIsUseDefaultConfiguration;
  private ISVNOptions myOptions;
  private List<String> myCheckoutURLs;
  private boolean myIsKeepLocks;
  private String myLastSelectedCheckoutURL;
  private boolean myRemoteStatus;
  private ISVNAuthenticationManager myAuthManager;

  public static final AuthStorage RUNTIME_AUTH_CACHE = new AuthStorage();
  public boolean PROCESS_UNRESOLVED = false;
  public String LAST_MERGED_REVISION = null;
  public boolean UPDATE_RUN_STATUS = false;
  public boolean UPDATE_RECURSIVELY = true;
  public boolean MERGE_DRY_RUN = false;

  private final Map<File, MergeRootInfo> myMergeRootInfos = new HashMap<File, MergeRootInfo>();
  private final Map<File, UpdateRootInfo> myUpdateRootInfos = new HashMap<File, UpdateRootInfo>();


  public static SvnConfiguration getInstance(Project project) {
    return project.getComponent(SvnConfiguration.class);
  }

  public String getConfigurationDirectory() {
    if (myConfigurationDirectory == null || isUseDefaultConfiguation()) {
      myConfigurationDirectory = SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath();
    }
    return myConfigurationDirectory;
  }

  public boolean isUseDefaultConfiguation() {
    return myIsUseDefaultConfiguration;
  }

  public void setConfigurationDirectory(String path) {
    myConfigurationDirectory = path;
    File dir = path == null ? SVNWCUtil.getDefaultConfigurationDirectory() : new File(path);
    SVNConfigFile.createDefaultConfiguration(dir);

    myOptions = null;
    myAuthManager = null;
    RUNTIME_AUTH_CACHE.clear();
  }

  public void setUseDefaultConfiguation(boolean useDefault) {
    myIsUseDefaultConfiguration = useDefault;
    myOptions = null;
    myAuthManager = null;
    RUNTIME_AUTH_CACHE.clear();
  }

  public ISVNOptions getOptions(Project project) {
    if (myOptions == null) {
      File path = new File(getConfigurationDirectory());
      myOptions = SVNWCUtil.createDefaultOptions(path.getAbsoluteFile(), true);
    }
    return myOptions;
  }

  public ISVNAuthenticationManager getAuthenticationManager(Project project) {
    if (myAuthManager == null) {
        myAuthManager = new SvnAuthenticationManager();
        myAuthManager.setAuthenticationProvider(new SvnAuthenticationProvider(project));
        myAuthManager.setRuntimeStorage(RUNTIME_AUTH_CACHE);
    }
    return myAuthManager;
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
    List urls = element.getChildren("checkoutURL");
    myCheckoutURLs = new LinkedList<String>();
    for (Object url1 : urls) {
      Element child = (Element)url1;
      String url = child.getText();
      if (url != null) {
        myCheckoutURLs.add(url);
      }
    }
    myIsKeepLocks = element.getChild("keepLocks") != null;
    myRemoteStatus = element.getChild("remoteStatus") != null;
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
    if (myCheckoutURLs != null) {
      for (final String url : myCheckoutURLs) {
        Element urlElement = new Element("checkoutURL");
        urlElement.setText(url);
        if (url.equals(myLastSelectedCheckoutURL)) {
          urlElement.setAttribute("active", "true");
        }
        element.addContent(urlElement);
      }
    }
    if (myIsKeepLocks) {
      element.addContent(new Element("keepLocks"));
    }
    if (myRemoteStatus) {
      element.addContent(new Element("remoteStatus"));
    }
  }

  public Collection getCheckoutURLs() {
    if (myCheckoutURLs == null) {
      myCheckoutURLs = new LinkedList<String>();
    }
    return myCheckoutURLs;
  }

  public void addCheckoutURL(String url) {
    if (myCheckoutURLs == null) {
      myCheckoutURLs = new LinkedList<String>();
    }
    if (myCheckoutURLs.contains(url)) {
      return;
    }
    myCheckoutURLs.add(0, url);
  }

  public void removeCheckoutURL(String url) {
    if (myCheckoutURLs != null) {
      myCheckoutURLs.remove(url);
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "SvnConfiguration";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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

  public static class AuthStorage implements ISVNAuthenticationStorage {

    private Map<String, Object> myStorage = new Hashtable<String, Object>();

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

}
