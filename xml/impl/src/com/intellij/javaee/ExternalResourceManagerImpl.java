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
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.util.XmlUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author mike
 */
@State(name = "ExternalResourceManagerImpl",
       storages = {@Storage(id = "other", file = "$APP_CONFIG$/other.xml")})
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable {
  static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";


  private final Map<String, Map<String, String>> myResources = new HashMap<String, Map<String, String>>();
  private final Set<String> myResourceLocations = new HashSet<String>();

  private final Set<String> myIgnoredResources = new HashSet<String>();

  private final AtomicNotNullLazyValue<Map<String, Map<String, String>>> myStdResources = new AtomicNotNullLazyValue<Map<String, Map<String, String>>>() {
        
    @NotNull
    @Override
    protected Map<String, Map<String, String>> compute() {
      return computeStdResources();
    }
  };

  protected Map<String, Map<String, String>> computeStdResources() {
    ResourceRegistrarImpl registrar = new ResourceRegistrarImpl();
    for (StandardResourceProvider provider : Extensions.getExtensions(StandardResourceProvider.EP_NAME)) {
      provider.registerResources(registrar);
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
  static Map<String, String> getMap(@NotNull final Map<String, Map<String, String>> resources,
                                    @Nullable final String version,
                                    final boolean create) {
    Map<String, String> map = resources.get(version);
    if (map == null) {
      if (create) {
        map = new HashMap<String, String>();
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
    String result = getUserResourse(url, version);
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
    String resourse = getProjectResources(project).getUserResourse(url, version);
    return resourse == null ? getUserResourse(url, version) : resourse;
  }

  @Override
  @Nullable
  public String getStdResource(String url, String version) {
    Map<String, String> map = getMap(myStdResources.getValue(), version, false);
    return map != null ? map.get(url) : null;
  }

  @Nullable
  private String getUserResourse(String url, String version) {
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
    addResourcesFromMap(fileType, result, version, myResources);

    if (includeStandard) {
      addResourcesFromMap(fileType, result, version, myStdResources.getValue());
    }

    return ArrayUtil.toStringArray(result);
  }

  private static void addResourcesFromMap(@Nullable final FileType fileType,
                                          final List<String> result,
                                          String version,
                                          Map<String, Map<String, String>> resourcesMap) {
    Map<String, String> resources = getMap(resourcesMap, version, false);
    if (resources == null) return;
    final Set<String> keySet = resources.keySet();

    for (String key : keySet) {
      String resource = resources.get(key);

      if (fileType != null) {
        String defaultExtension = fileType.getDefaultExtension();

        if (resource.endsWith(defaultExtension) &&
            resource.length() > defaultExtension.length() &&
            resource.charAt(resource.length() - defaultExtension.length() - 1) == '.') {
          result.add(key);
        }
      }
      else {
        result.add(key);
      }
    }
  }

  public void addResource(String url, String location) {
    addResource(url, DEFAULT_VERSION, location);
  }

  public void addResource(@NonNls String url, @NonNls String version, @NonNls String location) {
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
    clearAllResources();
    getProjectResources(project).clearAllResources();
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public void addIgnoredResource(String url) {
    addIgnoredSilently(url);
    fireExternalResourceChanged();
  }

  private void addIgnoredSilently(String url) {
    myIgnoredResources.add(url);
    myModificationCount++;
  }

  public void removeIgnoredResource(String url) {
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
      if (namespaceDescriptorProvider.getNamespaceDescriptor(null, url) != null) return true;
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

  public List<String> getStandardResources() {
    ArrayList<String> strings = new ArrayList<String>();
    Collection<Map<String, String>> maps = myStdResources.getValue().values();
    for (Map<String, String> map : maps) {
      strings.addAll(map.values());
    }
    return strings;
  }

  private final static NotNullLazyKey<ProjectResources, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(ProjectResources.class);

  private static ExternalResourceManagerImpl getProjectResources(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

}
