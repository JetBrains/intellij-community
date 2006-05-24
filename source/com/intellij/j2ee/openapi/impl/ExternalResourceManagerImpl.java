package com.intellij.j2ee.openapi.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;
import java.net.URL;

/**
 * @author mike
 */
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable, ApplicationComponent, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls private static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls private static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls private static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls private static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";
  @NonNls private static final String IBM_NS = "http://www.ibm.com/webservices/xsd/";
  @NonNls private static final String PERSISTENCE_NS = "http://java.sun.com/xml/ns/persistence";
  @NonNls private static final String PERSISTENCE_ORM_NS = "http://java.sun.com/xml/ns/persistence/orm";

  private Map<String, String> myResources = new com.intellij.util.containers.HashMap<String, String>();
  private Set<String> myIgnoredResources = new HashSet<String>();
  private Map<String, String> myStdResources = new com.intellij.util.containers.HashMap<String, String>();
  private List<ExternalResourceListener> myListeners = new ArrayList<ExternalResourceListener>();
  private long myModificationCount = 0;
  private PathMacrosImpl myPathMacros;
  @NonNls protected static final String RESOURCE_ELEMENT = "resource";
  @NonNls protected static final String URL_ATTR = "url";
  @NonNls protected static final String LOCATION_ATTR = "location";
  @NonNls protected static final String IGNORED_RESOURCE_ELEMENT = "ignored-resource";

  public ExternalResourceManagerImpl(PathMacrosImpl pathMacros) {
    addInternalResource(J2EE_1_3 + "connector_1_0.dtd", "connector_1_0.dtd");
    addInternalResource(J2EE_1_3 + "jspxml.xsd", "jspxml.xsd");
    addInternalResource(J2EE_1_3 + "jspxml.dtd", "jspxml.dtd");
    addInternalResource(XmlUtil.JSP_URI,"jspxml2.xsd");
    addInternalResource("http://java.sun.com/products/jsp/dtd/jsp_1_0.dtd","jspxml.dtd");

    addInternalResource(J2EE_1_3 + "web-jsptaglibrary_1_2.dtd", "web-jsptaglibrary_1_2.dtd");
    addInternalResource(J2EE_1_3 +  "web-app_2_3.dtd", "web-app_2_3.dtd");
    addInternalResource(J2EE_1_3 +  "application-client_1_3.dtd", "application-client_1_3.dtd");
    addInternalResource(J2EE_1_3 +  "application_1_3.dtd", "application_1_3.dtd");
    addInternalResource(J2EE_1_3 +   "ejb-jar_2_0.dtd", "ejb-jar_2_0.dtd");
    addInternalResource(J2EE_1_3 +   "logger.dtd", "logger.dtd");
    addInternalResource(J2EE_1_3 +   "web-facesconfig_1_1.dtd", "web-facesconfig_1_1.dtd");

    addInternalResource(J2EE_1_2 +  "application-client_1_2.dtd", "application-client_1_2.dtd");
    addInternalResource(J2EE_1_2 +  "application_1_2.dtd", "application_1_2.dtd");
    addInternalResource(J2EE_1_2 +  "ejb-jar_1_1.dtd","ejb-jar_1_1.dtd");
    addInternalResource(J2EE_1_2 +  "web-app_2_2.dtd","web-app_2_2.dtd");
    addInternalResource(J2EE_1_2 +  "web-jsptaglibrary_1_1.dtd","web-jsptaglibrary_1_1.dtd");
    addInternalResource(IBM_NS + "j2ee_web_services_client_1_1.xsd","j2ee_web_services_client_1_1.xsd");

    addInternalResource(XmlUtil.XSLT_URI,"xslt-1_0.xsd");
    addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd");
    addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd");
    addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    addInternalResource("http://www.w3.org/XML/1998/namespace","xml.xsd");
    addInternalResource(XmlUtil.XHTML_URI,"xhtml1-transitional.xsd");

    addInternalResource("http://www.w3.org/TR/html4/strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/html4/loose.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/html4/frameset.dtd","xhtml1-frameset.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd","xhtml1-frameset.dtd");

    addInternalResource(J2EE_NS + "web-app_2_4.xsd","web-app_2_4.xsd");
    addInternalResource(J2EE_NS + "ejb-jar_2_1.xsd","ejb-jar_2_1.xsd");
    addInternalResource(J2EE_NS + "connector_1_5.xsd","connector_1_5.xsd");
    addInternalResource(J2EE_NS + "jsp_2_0.xsd","jsp_2_0.xsd");
    addInternalResource(J2EE_NS + "j2ee_1_4.xsd","j2ee_1_4.xsd");
    addInternalResource(J2EE_NS + "application-client_1_4.xsd","application-client_1_4.xsd");
    addInternalResource(J2EE_NS + "application_1_4.xsd","application_1_4.xsd");
    addInternalResource(J2EE_NS + "web-jsptaglibrary_2_0.xsd","web-jsptaglibrary_2_0.xsd");

    addInternalResource(JAVAEE_NS + "javaee_5.xsd","javaee_5.xsd");
    addInternalResource(JAVAEE_NS + "javaee_web_services_client_1_2.xsd","javaee_web_services_client_1_2.xsd");
    addInternalResource(JAVAEE_NS + "application_5.xsd","application_5.xsd");
    addInternalResource(JAVAEE_NS + "ejb-jar_3_0.xsd","ejb-jar_3_0.xsd");
    addInternalResource(JAVAEE_NS + "web-app_2_5.xsd","web-app_2_5.xsd");
    addInternalResource(JAVAEE_NS + "application-client_5.xsd","application-client_5.xsd");
    addInternalResource(JAVAEE_NS + "javaee_web_services_1_2.xsd","javaee_web_services_1_2.xsd");
    addInternalResource(JAVAEE_NS + "jsp_2_0.xsd","jsp_2_0.xsd");
    addInternalResource(JAVAEE_NS + "web-jsptaglibrary_2_1.xsd","web-jsptaglibrary_2_1.xsd");
    addInternalResource(JAVAEE_NS + "web-facesconfig_1_2.xsd","web-facesconfig_1_2.xsd");

    addInternalResource(PERSISTENCE_NS,"persistence.xsd");
    addInternalResource(PERSISTENCE_ORM_NS,"orm_1_0.xsd");


    // Plugins DTDs // stathik
    addInternalResource("http://plugins.intellij.net/plugin.dtd",
                        "plugin.dtd");
    addInternalResource("http://plugins.intellij.net/plugin-repository.dtd",
                        "plugin-repository.dtd");
    myPathMacros = pathMacros;
  }

  private static String getFile(String name, Class klass) {
    final URL resource = klass.getResource(name);
    if (resource == null) return null;
    
    String path = FileUtil.unquote(resource.toString());
    // this is done by FileUtil for windows
    path = path.replace('\\','/');
    return path;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  private void addInternalResource(@NonNls String resource, @NonNls String fileName) {
    addStdResource(resource, "/standardSchemas/" + fileName);
  }

  public void addStdResource(@NonNls String resource, @NonNls String fileName){
    addStdResource(resource, fileName, getClass());
  }

  public void addStdResource(String resource, String fileName, Class klass) {
    final String file = getFile(fileName, klass);
    if (file != null) {
      myStdResources.put(resource, file);
    }
    else {
      LOG.info("Cannot find standard resource. filename:" + fileName + " klass=" + klass);
    }
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

  public String[] getResourceUrls(FileType fileType, final boolean includeStandard) {
    final List<String> result = new LinkedList<String>();
    addResourcesFromMap(fileType, result,myResources);

    if (includeStandard) {
      addResourcesFromMap(fileType, result,myStdResources);
    }

    return result.toArray(new String[result.size()]);
  }

  private void addResourcesFromMap(final FileType fileType, final List<String> result, Map<String, String> resources) {
    final Set<String> keySet = resources.keySet();

    for (String key : keySet) {
      String resource = resources.get(key);

      if (fileType != null) {
        String defaultExtension = fileType.getDefaultExtension();

        if (resource.endsWith(defaultExtension) &&
            resource.length() > defaultExtension.length() &&
            resource.charAt(resource.length() - defaultExtension.length() - 1) == '.'
          ) {
          result.add(key);
        }
      }
      else {
        result.add(key);
      }
    }
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

  public void removeIgnoredResource(String url) {
    if(myIgnoredResources.remove(url)) {
      myModificationCount++;
      fireExternalResourceChanged();
    }
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
    for (final Object o1 : element.getChildren(RESOURCE_ELEMENT)) {
      Element e = (Element)o1;
      addResource(e.getAttributeValue(URL_ATTR), e.getAttributeValue(LOCATION_ATTR).replace('/', File.separatorChar));
    }

    for (final Object o : element.getChildren(IGNORED_RESOURCE_ELEMENT)) {
      Element e = (Element)o;
      addIgnoredResource(e.getAttributeValue(URL_ATTR));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final String[] urls = getAvailableUrls();
    for (String url : urls) {
      String location = getResourceLocation(url);

      final Element e = new Element(RESOURCE_ELEMENT);

      e.setAttribute(URL_ATTR, url);
      e.setAttribute(LOCATION_ATTR, location.replace(File.separatorChar, '/'));
      element.addContent(e);
    }

    final String[] ignoredResources = getIgnoredResources();
    for (String ignoredResource : ignoredResources) {
      final Element e = new Element(IGNORED_RESOURCE_ELEMENT);

      e.setAttribute(URL_ATTR, ignoredResource);
      element.addContent(e);
    }

    final ReplacePathToMacroMap macroReplacements = new ReplacePathToMacroMap();
    PathMacrosImpl.getInstanceEx().addMacroReplacements(macroReplacements);
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
    for (ExternalResourceListener listener : listeners) {
      listener.externalResourceChanged();
    }
  }
}
