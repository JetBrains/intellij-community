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
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class SvnApplicationSettings implements ApplicationComponent, JDOMExternalizable {

  private SvnFileSystemListener myVFSHandler;
  private Map myAuthenticationInfo;

  public static SvnApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SvnApplicationSettings.class);
  }

  public String getComponentName() {
    return "SvnApplicationSettings";
  }

  public void initComponent() {
    if (myVFSHandler == null) {
      myVFSHandler = new SvnFileSystemListener();
      LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().addCommandListener(myVFSHandler);
    }
  }

  public void disposeComponent() {
    SVNGanymedSession.shutdown();
    if (myVFSHandler != null) {
      LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().removeCommandListener(myVFSHandler);
      myVFSHandler = null;
    }
  }

  public void readExternal(Element element) throws InvalidDataException {

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
      Element authElement = document.getRootElement().getChild("credentials");
      if (authElement == null) {
          return;
      }
      myAuthenticationInfo = new HashMap();
      List realmsList = authElement.getChildren("realm");
      for (Iterator realms = realmsList.iterator(); realms.hasNext();) {
          Element realmElement = (Element) realms.next();
          String realmName = realmElement.getAttributeValue("name");
          StringBuffer sb = new StringBuffer(realmName);

          byte[] buffer = new byte[sb.length()];
          int length = SVNBase64.base64ToByteArray(sb, buffer);
          realmName = new String(buffer, 0, length);
          Map infoMap = new HashMap();
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
          myAuthenticationInfo.put(realmName, infoMap);
      }
  }

  public void writeExternal(Element element) throws WriteExternalException {
      if (myAuthenticationInfo == null) {
          return;
      }
      Document document = new Document();
      Element authElement = new Element("credentials");
      for (Iterator realms = myAuthenticationInfo.keySet().iterator(); realms.hasNext();) {
          String realm = (String) realms.next();
          Element realmElement = new Element("realm");
          realmElement.setAttribute("name", SVNBase64.byteArrayToBase64(realm.getBytes()));
          Map info = (Map) myAuthenticationInfo.get(realm);
          for (Iterator keys = info.keySet().iterator(); keys.hasNext();) {
              String key = (String) keys.next();
              String value = (String) info.get(key);
              realmElement.setAttribute(key, value);
          }
          authElement.addContent(realmElement);
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

  public Map getAuthenticationInfo(String realm) {
    synchronized(this) {
        if (myAuthenticationInfo != null) {
            Map info = (Map) myAuthenticationInfo.get(realm);
            if (info != null) {
                return decodeData(info);
            }
        }
    }
    return null;
  }

  public void saveAuthenticationInfo(String realm, Map info) {
      synchronized(this) {
        if (info == null) {
            return;
        }
        realm = realm == null ? "default" : realm;
        if (myAuthenticationInfo == null) {
            myAuthenticationInfo = new HashMap();
        }
        myAuthenticationInfo.put(realm, encodeData(info));
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

  private static Map encodeData(Map source) {
      Map dst = new HashMap();
      for (Iterator keys = source.keySet().iterator(); keys.hasNext();) {
          String key = (String) keys.next();
          String value = (String) source.get(key);
          if (key != null && value != null) {
              dst.put(key, SVNBase64.byteArrayToBase64(value.getBytes()));
          }
      }
      return dst;
  }
  private static Map decodeData(Map source) {
      Map dst = new HashMap();
      for (Iterator keys = source.keySet().iterator(); keys.hasNext();) {
          String key = (String) keys.next();
          String value = (String) source.get(key);
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
      File file = new File(SVNWCUtil.getDefaultConfigurationDirectory(), "auth");
      return new File(file, "svn.idea/credentials.xml");
  }
}
