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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.tmatesoft.svn.core.internal.io.svn.SVNGanymedSession;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class SvnApplicationSettings implements ApplicationComponent, JDOMExternalizable {

  private SvnFileSystemListener myVFSHandler;
  private Map<String, Map<String, Map<String, String>>> myAuthenticationInfo;
  private List<String> myCheckoutURLs;
  private int mySvnProjectCount;

  public static SvnApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SvnApplicationSettings.class);
  }

  @NotNull
  public String getComponentName() {
    return "SvnApplicationSettings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    SVNGanymedSession.shutdown();
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
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    readCredentials();

    myCheckoutURLs = new ArrayList<String>();
    List urls = element.getChildren("checkoutURL");
    for (Object url1 : urls) {
      Element child = (Element)url1;
      String url = child.getText();
      if (url != null) {
        myCheckoutURLs.add(url);
      }
    }
  }

  private void readCredentials() {
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

  public void writeExternal(Element element) throws WriteExternalException {
    writeCredentials();

    if (myCheckoutURLs != null) {
      for (final String url : myCheckoutURLs) {
        Element urlElement = new Element("checkoutURL");
        urlElement.setText(url);
        element.addContent(urlElement);
      }
    }
  }

  private void writeCredentials() {
    if (myAuthenticationInfo == null) {
        return;
    }
    Document document = new Document();
    Element authElement = new Element("kinds");
    for (Iterator<String> groups = myAuthenticationInfo.keySet().iterator(); groups.hasNext();) {
        String kind = groups.next();
        Element groupElement = new Element(kind);
        Map<String, Map<String, String>> groupsMap = myAuthenticationInfo.get(kind);

        for (Iterator<String> realms = groupsMap.keySet().iterator(); realms.hasNext();) {
          String realm = realms.next();
          Element realmElement = new Element("realm");
          realmElement.setAttribute("name", SVNBase64.byteArrayToBase64(realm.getBytes()));
          Map<String, String> info = groupsMap.get(realm);
          for (Iterator<String> keys = info.keySet().iterator(); keys.hasNext();) {
              String key = keys.next();
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
    synchronized(this) {
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
      // 'url' is not necessary an exact match for some of the urls in collection - it has been parsed and then converted back to string
      for(String oldUrl: myCheckoutURLs) {
        try {
          if (url.equals(oldUrl) || SVNURL.parseURIEncoded(url).equals(SVNURL.parseURIEncoded(oldUrl))) {
            myCheckoutURLs.remove(oldUrl);
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
