/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
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
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashMap;
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

/**
 * @author mike
 */
@State(name = "ExternalResourceManagerImpl",
       storages = {@Storage( file = "$APP_CONFIG$/other.xml")})
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable {
  static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";


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

  private String myDefaultHtmlDoctype = XmlUtil.XHTML_URI;

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

  private final List<ExternalResourceListener> myListeners = new ArrayList<ExternalResourceListener>();
  private long myModificationCount = 0;
  private final PathMacrosImpl myPathMacros;
  @NonNls private static final String RESOURCE_ELEMENT = "resource";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String LOCATION_ATTR = "location";
  @NonNls private static final String IGNORED_RESOURCE_ELEMENT = "ignored-resource";
  @NonNls private static final String HTML_DEFAULT_DOCTYPE_ELEMENT = "default-html-doctype";
  private static final String DEFAULT_VERSION = null;
  @NonNls public static final String STANDARD_SCHEMAS = "/standardSchemas/";

  public ExternalResourceManagerImpl(PathMacrosImpl pathMacros) {
    myPathMacros = pathMacros;
  }

  public static boolean isStandardResource(VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && parent.getName().equals("standardSchemas");
  }

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
        map = CollectionFactory.hashMap();
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
      result = getStdResource(url, version);
    }
    if (result == null) {
      result = url;
    }
    return result;
  }

  @Override
  @Nullable
  public String getUserResourse(Project project, String url, String version) {
    String resourse = getProjectResources(project).getUserResource(url, version);
    return resourse == null ? getUserResource(url, version) : resourse;
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
    if (child != null && child.getText() != null) {
      myDefaultHtmlDoctype = child.getText();
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

    if (myDefaultHtmlDoctype != null) {
      final Element e = new Element(HTML_DEFAULT_DOCTYPE_ELEMENT);
      e.setText(myDefaultHtmlDoctype);
      element.addContent(e);
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
    for (ExternalResourceListener listener : myListeners.toArray(new ExternalResourceListener[myListeners.size()])) {
      listener.externalResourceChanged();
    }
  }

  Collection<Map<String, Resource>> getStandardResources() {
    return myStdResources.getValue().values();
  }


  private static final NotNullLazyKey<ProjectResources, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(ProjectResources.class);

  private static ExternalResourceManagerImpl getProjectResources(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  @Override
  @NotNull
  public String getDefaultHtmlDoctype(@NotNull Project project) {
    return getProjectResources(project).myDefaultHtmlDoctype;
  }

  @Override
  public void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project) {
    getProjectResources(project).setDefaultHtmlDoctype(defaultHtmlDoctype);
  }

  private void setDefaultHtmlDoctype(String defaultHtmlDoctype) {
    myModificationCount++;
    myDefaultHtmlDoctype = defaultHtmlDoctype;
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
