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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.svn.SVNGanymedSession;
import org.tmatesoft.svn.core.internal.util.SVNBase64;

import java.io.File;
import java.io.IOException;
import java.util.*;


@State(
  name="SvnApplicationSettings",
  storages = {
    @Storage(
      id="SvnApplicationSettings",
      file="$APP_CONFIG$/other.xml"
    )}
)
public class SvnApplicationSettings implements PersistentStateComponent<SvnApplicationSettings.ConfigurationBean> {
  private SvnFileSystemListener myVFSHandler;
  private Map<String, Map<String, Map<String, String>>> myAuthenticationInfo;
  private int mySvnProjectCount;

  public static class ConfigurationBean {
    public List<String> myCheckoutURLs = new ArrayList<String>();
  }

  private ConfigurationBean myConfigurationBean;
  private boolean myCredentialsLoaded = false;
  private boolean myCredentialsModified = false;

  public static SvnApplicationSettings getInstance() {
    return ServiceManager.getService(SvnApplicationSettings.class);
  }

  public SvnApplicationSettings() {
    myConfigurationBean = new ConfigurationBean();
  }

  public ConfigurationBean getState() {
    if (myCredentialsModified) {
      writeCredentials();
    }
    return myConfigurationBean;
  }

  public void loadState(ConfigurationBean object) {
    myConfigurationBean = object;
  }

  public void svnActivated() {
    if (myVFSHandler == null) {
      myVFSHandler = new SvnFileSystemListener();
      LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().addCommandListener(myVFSHandler);
    }
    mySvnProjectCount++;
  }

  public void svnDeactivated() {
    mySvnProjectCount--;
    if (mySvnProjectCount == 0) {
      LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().removeCommandListener(myVFSHandler);
      myVFSHandler = null;
      SVNGanymedSession.shutdown();
    }
  }

  private void readCredentials() {
    myCredentialsLoaded = true;
    File file = getCredentialsFile();
    if (!file.exists() || !file.canRead() || !file.isFile()) {
      return;
    }
    Document document;
    try {
        document = JDOMUtil.loadDocument(file);
    } catch (JDOMException e) {
      return;
    } catch (IOException e) {
      return;
    }
    if (document == null || document.getRootElement() == null) {
      return;
    }
    Element authElement = document.getRootElement().getChild("kinds");

    if (authElement == null) {
      return;
    }
    myAuthenticationInfo = new HashMap<String, Map<String, Map<String, String>>>();
    List groupsList = authElement.getChildren();
    for (Iterator groups = groupsList.iterator(); groups.hasNext();) {
      Element groupElement = (Element) groups.next();
      String kind = groupElement.getName();
      if ("realm".equals(kind)) {
        // old version.
        continue;
      }
      Map<String, Map<String, String>> groupMap = new HashMap<String, Map<String, String>>();
      myAuthenticationInfo.put(kind, groupMap);
      List realmsList = groupElement.getChildren("realm");
      for (Iterator realms = realmsList.iterator(); realms.hasNext();) {
          Element realmElement = (Element) realms.next();
          String realmName = realmElement.getAttributeValue("name");
          StringBuffer sb = new StringBuffer(realmName);

          byte[] buffer = new byte[sb.length()];
          int length = SVNBase64.base64ToByteArray(sb, buffer);
          realmName = new String(buffer, 0, length);
          Map<String, String> infoMap = new HashMap<String, String>();
          List attrsList = realmElement.getAttributes();
          for (Iterator attrs = attrsList.iterator(); attrs.hasNext();) {
              Attribute attr = (Attribute) attrs.next();
              if ("name".equals(attr.getName())) {
                  continue;
              }
              String key = attr.getName();
              String value = attr.getValue();
              infoMap.put(key, value);
          }
          groupMap.put(realmName, infoMap);
      }
    }
  }

  private void writeCredentials() {
    myCredentialsModified = false;
    if (myAuthenticationInfo == null) {
        return;
    }
    Document document = new Document();
    Element authElement = new Element("kinds");
    for (String kind : myAuthenticationInfo.keySet()) {
      Element groupElement = new Element(kind);
      Map<String, Map<String, String>> groupsMap = myAuthenticationInfo.get(kind);

      for (String realm : groupsMap.keySet()) {
        Element realmElement = new Element("realm");
        realmElement.setAttribute("name", SVNBase64.byteArrayToBase64(realm.getBytes()));
        Map<String, String> info = groupsMap.get(realm);
        for (String key : info.keySet()) {
          String value = info.get(key);
          realmElement.setAttribute(key, value);
        }
        groupElement.addContent(realmElement);
      }
      authElement.addContent(groupElement);
    }
    document.setRootElement(new Element("svn4idea"));
    document.getRootElement().addContent(authElement);

    File file = getCredentialsFile();
    file.getParentFile().mkdirs();
    try {
        JDOMUtil.writeDocument(document, file, System.getProperty("line.separator"));
    } catch (IOException e) {
        //
    }
  }

  public Map<String, String> getAuthenticationInfo(String realm, String kind) {
    synchronized (this) {
      if (!myCredentialsLoaded) {
        readCredentials();
      }
      if (myAuthenticationInfo != null) {
        Map<String, Map<String, String>> group = myAuthenticationInfo.get(kind);
        if (group != null) {
          Map<String, String> info = group.get(realm);
          if (info != null) {
            return decodeData(info);
          }
        }
      }
    }
    return null;
  }

  public void saveAuthenticationInfo(String realm, String kind, Map<String, String> info) {
      synchronized(this) {
        if (info == null) {
            return;
        }
        myCredentialsModified = true;
        realm = realm == null ? "default" : realm;
        if (myAuthenticationInfo == null) {
            myAuthenticationInfo = new HashMap<String, Map<String, Map<String, String>>>();
        }
        Map<String, Map<String, String>> group = myAuthenticationInfo.get(kind);
        if (group == null) {
          group = new HashMap<String, Map<String, String>>();
          myAuthenticationInfo.put(kind, group);
        }
        group.put(realm, encodeData(info));
      }
  }

  public void clearAuthenticationInfo() {
      synchronized(this) {
        if (!myCredentialsLoaded) {
          readCredentials();
        }
        if (myAuthenticationInfo == null) {
            return;
        }
        myAuthenticationInfo.clear();
      }
  }

  private static Map<String, String> encodeData(Map<String, String> source) {
    Map<String, String> dst = new HashMap<String, String>();
    for (final String key : source.keySet()) {
      String value = source.get(key);
      if (key != null && value != null) {
        dst.put(key, SVNBase64.byteArrayToBase64(value.getBytes()));
      }
    }
    return dst;
  }
  private static Map<String, String> decodeData(Map<String, String> source) {
    Map<String, String> dst = new HashMap<String, String>();
    for (String key : source.keySet()) {
      String value = source.get(key);
      if (key != null && value != null) {
        StringBuffer sb = new StringBuffer(value);
        byte[] buffer = new byte[sb.length()];
        int length = SVNBase64.base64ToByteArray(sb, buffer);
        dst.put(key, new String(buffer, 0, length));
      }
    }
    return dst;
  }

  public static File getCredentialsFile() {
    File file = new File(PathManager.getSystemPath());
    file = new File(file, "plugins");
    file = new File(file, "svn4idea");
    file.mkdirs();
    return new File(file, "credentials.xml");
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
}
