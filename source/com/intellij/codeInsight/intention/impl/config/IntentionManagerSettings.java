/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 23, 2002
 * Time: 8:15:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import org.jdom.Element;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.*;
import java.io.IOException;
import java.io.InputStream;

public class IntentionManagerSettings implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings");

  private Set<String> myIgnoredActions = new LinkedHashSet<String>();

  private Map<String,IntentionActionMetaData> myMetaData = new LinkedHashMap<String, IntentionActionMetaData>();
  static final String EXAMPLE_USAGE_URL_SUFFIX = ".template";

  public String getExternalFileName() {
    return "intentionSettings";
  }

  public static IntentionManagerSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(IntentionManagerSettings.class);
  }

  public String getComponentName() {
    return "IntentionManagerSettings";
  }

  public void initComponent() { }

  public void registerIntentionMetaData(IntentionAction intentionAction, String[] category) {
    registerIntentionMetaData(intentionAction, category, intentionAction.getFamilyName());
  }
  public void registerIntentionMetaData(IntentionAction intentionAction, String[] category, String descriptionDirectoryName) {
    try {
      URL dirURL = getIntentionDescriptionDirURL(intentionAction.getClass(), descriptionDirectoryName);
      LOG.assertTrue(dirURL != null, "Intention description directory not found: '"+descriptionDirectoryName+"'");
      URL[] beforeUrls = retrieveURLs(dirURL, "before", EXAMPLE_USAGE_URL_SUFFIX);
      URL[] afterUrls = retrieveURLs(dirURL, "after", EXAMPLE_USAGE_URL_SUFFIX);
      URL descriptionUrl = new URL(dirURL.toExternalForm() + "/description.html");
      registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), beforeUrls, afterUrls, descriptionUrl, category));
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }
  }

  private static URL[] retrieveURLs(URL descriptionDirectory, String prefix, String suffix) throws MalformedURLException {
    List<URL> urls = new ArrayList<URL>();
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      final String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        for (int i = 0; ; i++) {
          URL url = new URL(descriptionDirectory.toExternalForm() + "/" +
                            prefix + "." + extension + (i == 0 ? "" : Integer.toString(i)) +
                            suffix);
          try {
            InputStream inputStream = url.openStream();
            inputStream.close();
            urls.add(url);
          }
          catch (IOException ioe) {
            break;
          }
        }
      }
    }
    return urls.toArray(new URL[urls.size()]);
  }

  public static URL getIntentionDescriptionDirURL(Class aClass, String prefix) {
    URL pageURL = aClass.getClassLoader().getResource("intentionDescriptions/" + prefix);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Class:"+aClass.getName());
      LOG.debug("Classloader:"+aClass.getClassLoader());
      LOG.debug("Path:"+"intentionDescriptions/" + prefix);
      LOG.debug("URL:"+pageURL);
    }
    return pageURL;
  }

  public void disposeComponent() {
  }

  public boolean isShowLightBulb(IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  public void setShowLightBulb(IntentionAction action, boolean show) {
    if (show) {
      myIgnoredActions.remove(action.getFamilyName());
    }
    else {
      myIgnoredActions.add(action.getFamilyName());
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    myIgnoredActions.clear();
    List children = element.getChildren("ignoreAction");
    for (final Object aChildren : children) {
      Element e = (Element)aChildren;
      myIgnoredActions.add(e.getAttributeValue("name"));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (String name : myIgnoredActions) {
      element.addContent(new Element("ignoreAction").setAttribute("name", name));
    }
  }

  public List<IntentionActionMetaData> getMetaData() {
    return new ArrayList<IntentionActionMetaData>(myMetaData.values());
  }

  public boolean isEnabled(String family) {
    return !myIgnoredActions.contains(family);
  }
  public void setEnabled(String family, boolean enabled) {
    if (enabled) {
      myIgnoredActions.remove(family);
    }
    else {
      myIgnoredActions.add(family);
    }
  }

  public void registerMetaData(IntentionActionMetaData metaData) {
    //LOG.assertTrue(!myMetaData.containsKey(metaData.myFamily), "Action '"+metaData.myFamily+"' already registered");
    myMetaData.put(metaData.myFamily, metaData);
  }
}
