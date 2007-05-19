package com.intellij.j2ee.openapi.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * @author mike
 */
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable, ApplicationComponent, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";


  private Map<String, String> myResources = new HashMap<String, String>();
  private Set<String> myIgnoredResources = new HashSet<String>();
  private Map<String, String> myStdResources = new HashMap<String, String>();
  private List<ExternalResourceListener> myListeners = new ArrayList<ExternalResourceListener>();
  private long myModificationCount = 0;
  private PathMacrosImpl myPathMacros;
  @NonNls protected static final String RESOURCE_ELEMENT = "resource";
  @NonNls protected static final String URL_ATTR = "url";
  @NonNls protected static final String LOCATION_ATTR = "location";
  @NonNls protected static final String IGNORED_RESOURCE_ELEMENT = "ignored-resource";

  public ExternalResourceManagerImpl(PathMacrosImpl pathMacros) {
    addInternalResource(XmlUtil.XSLT_URI,"xslt-1_0.xsd");
    addInternalResource(XmlUtil.XINCLUDE_URI,"xinclude.xsd");
    addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd");
    addInternalResource("http://www.w3.org/2001/XMLSchema.dtd", "XMLSchema.dtd");
    addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd");
    addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    addInternalResource(XmlUtil.XML_NAMESPACE_URI,"xml.xsd");
    addInternalResource(XmlUtil.XHTML_URI,"xhtml1-transitional.xsd");

    addInternalResource("http://www.w3.org/TR/html4/strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/html4/loose.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/html4/frameset.dtd","xhtml1-frameset.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd","xhtml1-strict.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd","xhtml1-transitional.dtd");
    addInternalResource("http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd","xhtml1-frameset.dtd");

    // Plugins DTDs // stathik
    addInternalResource("http://plugins.intellij.net/plugin.dtd", "plugin.dtd");
    addInternalResource("http://plugins.intellij.net/plugin-repository.dtd", "plugin-repository.dtd");
    
    myPathMacros = pathMacros;

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final File extResourceFolder = new File(FetchExtResourceAction.getExternalResourcesPath());

      if (extResourceFolder.exists()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final VirtualFile extResourceDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(extResourceFolder);
            if (extResourceDir != null) LocalFileSystem.getInstance().addRootToWatch(extResourceDir.getPath(), true);
          }
        });
      }
    }
  }

  @Nullable
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

  public void addInternalResource(@NonNls String resource, @NonNls String fileName) {
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

  private static void addResourcesFromMap(final FileType fileType, final List<String> result, Map<String, String> resources) {
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
    macroExpands.substitute(element, SystemInfo.isFileSystemCaseSensitive, null);

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
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }

  @NotNull
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
