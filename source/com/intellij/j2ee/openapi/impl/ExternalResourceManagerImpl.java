package com.intellij.j2ee.openapi.impl;

import com.intellij.application.options.PathMacros;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Element;

import java.io.File;
import java.util.*;

/**
 * @author mike
 */
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable, ApplicationComponent, ModificationTracker {

  private static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  private static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  private static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  private static final String IBM_NS = "http://www.ibm.com/webservices/xsd/";

  private Map<String, String> myResources = new com.intellij.util.containers.HashMap<String, String>();
  private Set<String> myIgnoredResources = new HashSet<String>();
  private Map<String, String> myStdResources = new com.intellij.util.containers.HashMap<String, String>();
  private List<ExternalResourceListener> myListeners = new ArrayList<ExternalResourceListener>();
  private long myModificationCount = 0;
  private PathMacros myPathMacros;

  public ExternalResourceManagerImpl(PathMacros pathMacros) {
    addInternalResource(J2EE_1_3 + "connector_1_0.dtd", "connector_1_0.dtd");
    addInternalResource(J2EE_1_3 + "jspxml.xsd", "jspxml.xsd");
    addInternalResource(J2EE_1_3 + "jspxml.dtd", "jspxml.dtd");
    addInternalResource(XmlUtil.JSP_NAMESPACE,"jspxml.xsd");
    addInternalResource("http://java.sun.com/products/jsp/dtd/jsp_1_0.dtd","jspxml.dtd");

    addInternalResource(J2EE_1_3 + "web-jsptaglibrary_1_2.dtd", "web-jsptaglibrary_1_2.dtd");
    addInternalResource(J2EE_1_3 +  "web-app_2_3.dtd", "web-app_2_3.dtd");
    addInternalResource(J2EE_1_3 +  "application-client_1_3.dtd", "application-client_1_3.dtd");
    addInternalResource(J2EE_1_3 +  "application_1_3.dtd", "application_1_3.dtd");
    addInternalResource(J2EE_1_3 +   "ejb-jar_2_0.dtd", "ejb-jar_2_0.dtd");
    addInternalResource(J2EE_1_3 +   "logger.dtd", "logger.dtd");

    addInternalResource(J2EE_1_2 +  "application-client_1_2.dtd", "application-client_1_2.dtd");
    addInternalResource(J2EE_1_2 +  "application_1_2.dtd", "application_1_2.dtd");
    addInternalResource(J2EE_1_2 +  "ejb-jar_1_1.dtd","ejb-jar_1_1.dtd");
    addInternalResource(J2EE_1_2 +  "web-app_2_2.dtd","web-app_2_2.dtd");
    addInternalResource(J2EE_1_2 +  "web-jsptaglibrary_1_1.dtd","web-jsptaglibrary_1_1.dtd");
    addInternalResource(IBM_NS + "j2ee_web_services_client_1_1.xsd","j2ee_web_services_client_1_1.xsd");


    addInternalResource("http://www.w3.org/2001/XMLSchema", "XMLSchema.xsd");
    addInternalResource("http://www.w3.org/2001/XMLSchema-instance", "XMLSchema-instance.xsd");
    addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    addInternalResource("http://www.w3.org/1999/xhtml","xhtml1.dtd");
    addInternalResource("http://www.w3.org/1999/xhtml","xhtml1.dtd");

    addInternalResource("http://www.w3.org/TR/html4/strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/html4/loose.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/html4/frameset.dtd","xhtml1-frameset.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd","xhtml1-strict.dtd");

    addInternalResource(J2EE_NS + "web-app_2_4.xsd","web-app_2_4.xsd");
    addInternalResource(J2EE_NS + "ejb-jar_2_1.xsd","ejb-jar_2_1.xsd");
    addInternalResource(J2EE_NS + "connector_1_5.xsd","connector_1_5.xsd");
    addInternalResource(J2EE_NS + "jsp_2_0.xsd","jsp_2_0.xsd");
    addInternalResource(J2EE_NS + "j2ee_1_4.xsd","j2ee_1_4.xsd");
    addInternalResource(J2EE_NS + "application-client_1_4.xsd","application-client_1_4.xsd");
    addInternalResource(J2EE_NS + "application_1_4.xsd","application_1_4.xsd");
    addInternalResource(J2EE_NS + "web-jsptaglibrary_2_0.xsd","web-jsptaglibrary_2_0.xsd");

    // Plugins DTDs // stathik
    addInternalResource("http://plugins.intellij.net/plugin.dtd",
                       "plugin.dtd");
    addInternalResource("http://plugins.intellij.net/plugin-repository.dtd",
                       "plugin-repository.dtd");
    myPathMacros = pathMacros;
  }

  private static String getFile(String name, Class klass) {
    String path = FileUtil.unquote(klass.getResource(name).toString());
    // this is done by FileUtil for windows
    path = path.replace('\\','/');
    return path;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  private void addInternalResource(String resource, String fileName) {
    addStdResource(resource, "/standardSchemas/" + fileName);
  }

  public void addStdResource(String resource, String fileName){
    addStdResource(resource, fileName, getClass());
  }

  public void addStdResource(String resource, String fileName, Class klass) {
    myStdResources.put(resource, getFile(fileName, klass));
  }

  public String getResourceLocation(String url) {
    String result = myResources.get(url);

    if (result == null) {
      result = myStdResources.get(url);
    }
    if (result == null) {
      result = url;
    }

    return result;
  }

  public String[] getResourceUrls(FileType fileType) {
    final List<String> result = new LinkedList<String>();
    final Set<String> keySet = myResources.keySet();

    for (Iterator<String> iterator = keySet.iterator(); iterator.hasNext();) {
      String key = iterator.next();
      String resource = myResources.get(key);

      if (fileType!=null) {
        String defaultExtension = fileType.getDefaultExtension();

        if (resource.endsWith(defaultExtension) &&
            resource.length() > defaultExtension.length() &&
            resource.charAt(resource.length()-defaultExtension.length()-1) == '.'
            ) {
          result.add(key);
        }
      } else {
        result.add(key);
      }
    }

    return result.toArray(new String[result.size()]);
  }

  public void addResource(String url, String location) {
    myResources.put(url, location);
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public void removeResource(String url) {
    myResources.remove(url);
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public String[] getAvailableUrls() {
    final Set<String> set = myResources.keySet();
    return set.toArray(new String[set.size()]);
  }

  public void clearAllResources() {
    myResources.clear();
    myIgnoredResources.clear();
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public void addIgnoredResource(String url) {
    myIgnoredResources.add(url);
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public boolean isIgnoredResource(String url) {
    return myIgnoredResources.contains(url);
  }

  public String[] getIgnoredResources() {
    return myIgnoredResources.toArray(new String[myIgnoredResources.size()]);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final ExpandMacroToPathMap macroExpands = new ExpandMacroToPathMap();
    myPathMacros.addMacroExpands(macroExpands);
    macroExpands.substitute(element, SystemInfo.isFileSystemCaseSensitive);

    myModificationCount++;
    for (Iterator iterator = element.getChildren("resource").iterator(); iterator.hasNext();) {
      Element e = (Element)iterator.next();
      addResource(e.getAttributeValue("url"), e.getAttributeValue("location").replace('/', File.separatorChar));
    }

    for (Iterator i = element.getChildren("ignored-resource").iterator(); i.hasNext();) {
      Element e = (Element)i.next();
      addIgnoredResource(e.getAttributeValue("url"));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final String[] urls = getAvailableUrls();
    for (int i = 0; i < urls.length; i++) {
      String url = urls[i];
      String location = getResourceLocation(url);

      final Element e = new Element("resource");

      e.setAttribute("url", url);
      e.setAttribute("location", location.replace(File.separatorChar, '/'));
      element.addContent(e);
    }

    final String[] ignoredResources = getIgnoredResources();
    for (int i = 0; i < ignoredResources.length; i++) {
      String ignoredResource = ignoredResources[i];

      final Element e = new Element("ignored-resource");

      e.setAttribute("url", ignoredResource);
      element.addContent(e);
    }

    final ReplacePathToMacroMap macroReplacements = new ReplacePathToMacroMap();
    PathMacros.getInstance().addMacroReplacements(macroReplacements);
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public String getComponentName() {
    return "ExternalResourceManagerImpl";
  }

  public void addExteralResourceListener(ExternalResourceListener listener) {
    myListeners.add(listener);
  }

  public void removeExternalResourceListener(ExternalResourceListener listener) {
    myListeners.remove(listener);
  }

  private void fireExternalResourceChanged() {
    ExternalResourceListener[] listeners = myListeners.toArray(new ExternalResourceListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      ExternalResourceListener listener = listeners[i];
      listener.externalResourceChanged();
    }
  }
}
