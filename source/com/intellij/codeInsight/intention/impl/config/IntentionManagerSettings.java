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
      URL[] beforeUrls = retrieveURLs(dirURL, "before", ".java.template");
      URL[] afterUrls = retrieveURLs(dirURL, "after", ".java.template");
      URL descriptionUrl = new URL(dirURL.toExternalForm() + "/description.html");
      registerMetaData(new IntentionActionMetaData(intentionAction.getFamilyName(), beforeUrls, afterUrls, descriptionUrl, category));
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }
  }

  private URL[] retrieveURLs(URL descriptionDirectory, String prefix, String suffix) throws MalformedURLException {
    int i = 0;
    List<URL> urls = new ArrayList<URL>();
    while (true) {
      URL url = new URL(descriptionDirectory.toExternalForm() + "/" +
                            prefix + (i==0 ? "" : ""+i) +
                            suffix);
      try {
        InputStream inputStream = url.openStream();
        inputStream.close();
        urls.add(url);
      }
      catch (IOException e) {
        break;
      }
      i++;
    }
    return urls.toArray(new URL[urls.size()]);
  }

  public static URL getIntentionDescriptionDirURL(Class aClass, String prefix) {
    URL pageURL = aClass.getClassLoader().getResource("intentionDescriptions/" + prefix);
    return pageURL;
  }

  public void disposeComponent() {
  }

  public boolean isShowLightBulb(IntentionAction action) {
    return !myIgnoredActions.contains(action.getFamilyName());
  }

  public void setShowLightBulb(IntentionAction action, boolean show) {
    if (show) myIgnoredActions.remove(action.getFamilyName());
    else myIgnoredActions.add(action.getFamilyName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    myIgnoredActions.clear();
    List children = element.getChildren("ignoreAction");
    for (Iterator i = children.iterator(); i.hasNext();) {
      Element e = (Element)i.next();
      myIgnoredActions.add(e.getAttributeValue("name"));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<String> i = myIgnoredActions.iterator(); i.hasNext();) {
      String name = i.next();
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
