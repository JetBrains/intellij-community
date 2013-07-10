package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.net.URL;
import java.util.*;

public class ExternalResourceManagerExImpl extends ExternalResourceManagerEx {
  static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";
  private static final String CATALOG_PROPERTIES_ELEMENT = "CATALOG_PROPERTIES";


  private final Map<String, Map<String, String>> myResources = new HashMap<String, Map<String, String>>();
  private final Set<String> myResourceLocations = new HashSet<String>();

  private final Set<String> myIgnoredResources = new HashSet<String>();

  private final AtomicNotNullLazyValue<Map<String, Map<String, Resource>>> myStdResources = new AtomicNotNullLazyValue<Map<String, Map<String, Resource>>>() {

    @NotNull
    @Override
    protected Map<String, Map<String, Resource>> compute() {
      return computeStdResources();
    }
  };

  private String myDefaultHtmlDoctype = HTML5_DOCTYPE_ELEMENT;

  private String myCatalogPropertiesFile;
  private XMLCatalogManager myCatalogManager;
  private static final String HTML5_DOCTYPE_ELEMENT = "HTML5";

  protected Map<String, Map<String, Resource>> computeStdResources() {
    ResourceRegistrarImpl registrar = new ResourceRegistrarImpl();
    for (StandardResourceProvider provider : Extensions.getExtensions(StandardResourceProvider.EP_NAME)) {
      provider.registerResources(registrar);
    }
    StandardResourceEP[] extensions = Extensions.getExtensions(StandardResourceEP.EP_NAME);
    for (StandardResourceEP extension : extensions) {
      registrar.addStdResource(extension.url, extension.version, extension.resourcePath, null, extension.getLoaderForClass());
    }

    myIgnoredResources.addAll(registrar.getIgnored());
    return registrar.getResources();
  }

  private final List<ExternalResourceListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private long myModificationCount = 0;
  private final PathMacrosImpl myPathMacros;
  @NonNls private static final String RESOURCE_ELEMENT = "resource";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String LOCATION_ATTR = "location";
  @NonNls private static final String IGNORED_RESOURCE_ELEMENT = "ignored-resource";
  @NonNls private static final String HTML_DEFAULT_DOCTYPE_ELEMENT = "default-html-doctype";
  private static final String DEFAULT_VERSION = null;

  public ExternalResourceManagerExImpl(PathMacrosImpl pathMacros) {
    myPathMacros = pathMacros;
  }

  public boolean isStandardResource(VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && parent.getName().equals("standardSchemas");
  }

  @Override
  public boolean isUserResource(VirtualFile file) {
    return myResourceLocations.contains(file.getUrl());
  }

  @Nullable
  static <T> Map<String, T> getMap(@NotNull final Map<String, Map<String, T>> resources,
                                   @Nullable final String version,
                                   final boolean create) {
    Map<String, T> map = resources.get(version);
    if (map == null) {
      if (create) {
        map = ContainerUtil.newHashMap();
        resources.put(version, map);
      }
      else if (version == null || !version.equals(DEFAULT_VERSION)) {
        map = resources.get(DEFAULT_VERSION);
      }
    }

    return map;
  }

  public String getResourceLocation(String url) {
    return getResourceLocation(url, DEFAULT_VERSION);
  }

  public String getResourceLocation(@NonNls String url, String version) {
    String result = getUserResource(url, version);
    if (result == null) {
      XMLCatalogManager manager = getCatalogManager();
      if (manager != null) {
        result = manager.resolve(url);
      }
    }
    if (result == null) {
      result = getStdResource(url, version);
    }
    if (result == null) {
      result = url;
    }
    return result;
  }

  @Override
  @Nullable
  public String getUserResource(Project project, String url, String version) {
    String resource = getProjectResources(project).getUserResource(url, version);
    return resource == null ? getUserResource(url, version) : resource;
  }

  @Override
  @Nullable
  public String getStdResource(String url, String version) {
    Map<String, Resource> map = getMap(myStdResources.getValue(), version, false);
    if (map != null) {
      Resource resource = map.get(url);
      return resource == null ? null : resource.getResourceUrl();
    }
    else {
      return null;
    }
  }

  @Nullable
  private String getUserResource(String url, String version) {
    Map<String, String> map = getMap(myResources, version, false);
    return map != null ? map.get(url) : null;
  }

  public String getResourceLocation(@NonNls String url, @NotNull Project project) {
    String location = getProjectResources(project).getResourceLocation(url);
    return location == null || location.equals(url) ? getResourceLocation(url) : location;
  }

  public String getResourceLocation(@NonNls String url, String version, @NotNull Project project) {
    String location = getProjectResources(project).getResourceLocation(url, version);
    return location == null || location.equals(url) ? getResourceLocation(url, version) : location;
  }

  @Nullable
  public PsiFile getResourceLocation(@NotNull @NonNls final String url, @NotNull final PsiFile baseFile, final String version) {
    final XmlFile schema = XmlSchemaProvider.findSchema(url, baseFile);
    if (schema != null) {
      return schema;
    }
    final String location = getResourceLocation(url, version, baseFile.getProject());
    return XmlUtil.findXmlFile(baseFile, location);
  }

  public String[] getResourceUrls(FileType fileType, final boolean includeStandard) {
    return getResourceUrls(fileType, DEFAULT_VERSION, includeStandard);
  }

  public String[] getResourceUrls(@Nullable final FileType fileType, @NonNls final String version, final boolean includeStandard) {
    final List<String> result = new LinkedList<String>();
    addResourcesFromMap(result, version, myResources);

    if (includeStandard) {
      addResourcesFromMap(result, version, myStdResources.getValue());
    }

    return ArrayUtil.toStringArray(result);
  }

  private static <T> void addResourcesFromMap(final List<String> result,
                                              String version,
                                              Map<String, Map<String, T>> resourcesMap) {
    Map<String, T> resources = getMap(resourcesMap, version, false);
    if (resources == null) return;
    result.addAll(resources.keySet());
  }

  @TestOnly
  public static void addTestResource(final String url, final String location, Disposable parentDisposable) {
    final ExternalResourceManagerExImpl instance = (ExternalResourceManagerExImpl)getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        instance.addResource(url, location);
      }
    });
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            instance.removeResource(url);
          }
        });
      }
    });
  }
  public void addResource(String url, String location) {
    addResource(url, DEFAULT_VERSION, location);
  }

  public void addResource(@NonNls String url, @NonNls String version, @NonNls String location) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    addSilently(url, version, location);
    fireExternalResourceChanged();
  }

  private void addSilently(String url, String version, String location) {
    final Map<String, String> map = getMap(myResources, version, true);
    assert map != null;
    map.put(url, location);
    myResourceLocations.add(location);
    myModificationCount++;
  }

  public void removeResource(String url) {
    removeResource(url, DEFAULT_VERSION);
  }

  public void removeResource(String url, String version) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Map<String, String> map = getMap(myResources, version, false);
    if (map != null) {
      String location = map.remove(url);
      if (location != null) {
        myResourceLocations.remove(location);
      }
      myModificationCount++;
      fireExternalResourceChanged();
    }
  }

  @Override
  public void removeResource(String url, @NotNull Project project) {
    getProjectResources(project).removeResource(url);
  }

  @Override
  public void addResource(@NonNls String url, @NonNls String location, @NotNull Project project) {
    getProjectResources(project).addResource(url, location);
  }

  public String[] getAvailableUrls() {
    Set<String> urls = new HashSet<String>();
    for (Map<String, String> map : myResources.values()) {
      urls.addAll(map.keySet());
    }
    return ArrayUtil.toStringArray(urls);
  }

  @Override
  public String[] getAvailableUrls(Project project) {
    return getProjectResources(project).getAvailableUrls();
  }

  public void clearAllResources() {
    myResources.clear();
    myIgnoredResources.clear();
  }

  public void clearAllResources(Project project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    clearAllResources();
    getProjectResources(project).clearAllResources();
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public void addIgnoredResource(String url) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    addIgnoredSilently(url);
    fireExternalResourceChanged();
  }

  private void addIgnoredSilently(String url) {
    myIgnoredResources.add(url);
    myModificationCount++;
  }

  public void removeIgnoredResource(String url) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (myIgnoredResources.remove(url)) {
      myModificationCount++;
      fireExternalResourceChanged();
    }
  }

  public boolean isIgnoredResource(String url) {
    myStdResources.getValue();  // ensure ignored resources are loaded
    return myIgnoredResources.contains(url) || isImplicitNamespaceDescriptor(url);
  }

  private static boolean isImplicitNamespaceDescriptor(String url) {
    for (ImplicitNamespaceDescriptorProvider namespaceDescriptorProvider : Extensions
      .getExtensions(ImplicitNamespaceDescriptorProvider.EP_NAME)) {
      if (namespaceDescriptorProvider.getNamespaceDescriptor(null, url, null) != null) return true;
    }
    return false;
  }

  public String[] getIgnoredResources() {
    myStdResources.getValue();  // ensure ignored resources are loaded
    return ArrayUtil.toStringArray(myIgnoredResources);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  @Override
  public long getModificationCount(@NotNull Project project) {
    return getProjectResources(project).getModificationCount();
  }

  public void readExternal(Element element) throws InvalidDataException {
    final ExpandMacroToPathMap macroExpands = new ExpandMacroToPathMap();
    myPathMacros.addMacroExpands(macroExpands);
    macroExpands.substitute(element, SystemInfo.isFileSystemCaseSensitive);

    myModificationCount++;
    for (final Object o1 : element.getChildren(RESOURCE_ELEMENT)) {
      Element e = (Element)o1;
      addSilently(e.getAttributeValue(URL_ATTR), DEFAULT_VERSION, e.getAttributeValue(LOCATION_ATTR).replace('/', File.separatorChar));
    }

    for (final Object o : element.getChildren(IGNORED_RESOURCE_ELEMENT)) {
      Element e = (Element)o;
      addIgnoredSilently(e.getAttributeValue(URL_ATTR));
    }

    Element child = element.getChild(HTML_DEFAULT_DOCTYPE_ELEMENT);
    if (child != null) {
      String text = child.getText();
      if (FileUtil.toSystemIndependentName(text).endsWith(".jar!/resources/html5-schema/html5.rnc")) {
        text = HTML5_DOCTYPE_ELEMENT;
      }
      myDefaultHtmlDoctype = text;
    }
    Element catalogElement = element.getChild(CATALOG_PROPERTIES_ELEMENT);
    if (catalogElement != null) {
      myCatalogPropertiesFile = catalogElement.getTextTrim();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final String[] urls = getAvailableUrls();
    for (String url : urls) {
      if (url == null) continue;
      String location = getResourceLocation(url);
      if (location == null) continue;
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

    if (myDefaultHtmlDoctype != null && !HTML5_DOCTYPE_ELEMENT.equals(myDefaultHtmlDoctype)) {
      final Element e = new Element(HTML_DEFAULT_DOCTYPE_ELEMENT);
      e.setText(myDefaultHtmlDoctype);
      element.addContent(e);
    }
    if (myCatalogPropertiesFile != null) {
      Element properties = new Element(CATALOG_PROPERTIES_ELEMENT);
      properties.setText(myCatalogPropertiesFile);
      element.addContent(properties);
    }
    final ReplacePathToMacroMap macroReplacements = new ReplacePathToMacroMap();
    PathMacrosImpl.getInstanceEx().addMacroReplacements(macroReplacements);
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public void addExternalResourceListener(ExternalResourceListener listener) {
    myListeners.add(listener);
  }

  public void removeExternalResourceListener(ExternalResourceListener listener) {
    myListeners.remove(listener);
  }

  private void fireExternalResourceChanged() {
    for (ExternalResourceListener listener : myListeners) {
      listener.externalResourceChanged();
    }
  }

  Collection<Map<String, Resource>> getStandardResources() {
    return myStdResources.getValue().values();
  }


  protected ExternalResourceManagerExImpl getProjectResources(Project project) {
    return this;
  }

  @Override
  @NotNull
  public String getDefaultHtmlDoctype(@NotNull Project project) {
    final String doctype = getProjectResources(project).myDefaultHtmlDoctype;
    if (XmlUtil.XHTML_URI.equals(doctype)) {
      return XmlUtil.XHTML4_SCHEMA_LOCATION;
    }
    else if (HTML5_DOCTYPE_ELEMENT.equals(doctype)) {
      return Html5SchemaProvider.getHtml5SchemaLocation();
    }
    else {
      return doctype;
    }
  }

  @Override
  public void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project) {
    getProjectResources(project).setDefaultHtmlDoctype(defaultHtmlDoctype);
  }

  @Override
  public String getCatalogPropertiesFile() {
    return myCatalogPropertiesFile;
  }

  @Override
  public void setCatalogPropertiesFile(String filePath) {
    myCatalogManager = null;
    myCatalogPropertiesFile = filePath;
    myModificationCount++;
  }

  @Nullable
  private XMLCatalogManager getCatalogManager() {
    if (myCatalogManager == null && myCatalogPropertiesFile != null) {
      myCatalogManager = new XMLCatalogManager(myCatalogPropertiesFile);
    }
    return myCatalogManager;
  }

  private void setDefaultHtmlDoctype(String defaultHtmlDoctype) {
    myModificationCount++;

    if (Html5SchemaProvider.getHtml5SchemaLocation().equals(defaultHtmlDoctype)) {
      myDefaultHtmlDoctype = HTML5_DOCTYPE_ELEMENT;
    }
    else {
      myDefaultHtmlDoctype = defaultHtmlDoctype;
    }
    fireExternalResourceChanged();
  }

  @TestOnly
  public static void registerResourceTemporarily(final String url, final String location, Disposable disposable) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        getInstance().addResource(url, location);
      }
    });

    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            getInstance().removeResource(url);
          }
        });
      }
    });
  }

  static class Resource {
    String file;
    ClassLoader classLoader;
    Class clazz;

    @Nullable
    String getResourceUrl() {

      if (classLoader == null && clazz == null) return file;

      final URL resource = clazz == null ? classLoader.getResource(file) : clazz.getResource(file);
      classLoader = null;
      clazz = null;
      if (resource == null) {
        String message = "Cannot find standard resource. filename:" + file + " class=" + classLoader;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.error(message);
        }
        else {
          LOG.warn(message);
        }

        return null;
      }

      String path = FileUtil.unquote(resource.toString());
      // this is done by FileUtil for windows
      path = path.replace('\\','/');
      file = path;
      return path;
    }


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Resource resource = (Resource)o;

      if (classLoader != resource.classLoader) return false;
      if (clazz != resource.clazz) return false;
      if (file != null ? !file.equals(resource.file) : resource.file != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return file.hashCode();
    }

    @Override
    public String toString() {
      return file + " for " + classLoader;
    }
  }
}
