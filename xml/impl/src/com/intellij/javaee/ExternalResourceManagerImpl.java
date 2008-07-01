package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.index.XsdNamespaceBuilder;
import com.intellij.xml.index.XsdTagNameBuilder;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashMap;
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
public class ExternalResourceManagerImpl extends ExternalResourceManagerEx implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl");

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";


  private final Map<String, Map<String,String>> myResources = new HashMap<String, Map<String, String>>();
  private final Map<String, XmlNSDescriptorImpl> myImplicitNamespaces = new THashMap<String, XmlNSDescriptorImpl>();
  private final Set<String> myIgnoredResources = new HashSet<String>();
  private final Map<String, Map<String,String>> myStdResources = new HashMap<String, Map<String,String>>();

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
    addInternalResource(XmlUtil.XSLT_URI,"xslt-1_0.xsd");
    addInternalResource(XmlUtil.XSLT_URI,"2.0", "xslt-2_0.xsd");
    addInternalResource(XmlUtil.XINCLUDE_URI,"xinclude.xsd");
    addInternalResource(XmlUtil.XML_SCHEMA_URI, "XMLSchema.xsd");
    addInternalResource(XmlUtil.XML_SCHEMA_URI + ".xsd", "XMLSchema.xsd");
    addInternalResource("http://www.w3.org/2001/XMLSchema.dtd", "XMLSchema.dtd");
    addInternalResource(XmlUtil.XML_SCHEMA_INSTANCE_URI, "XMLSchema-instance.xsd");
    addInternalResource("http://www.w3.org/2001/xml.xsd","xml.xsd");
    addInternalResource(XmlUtil.XML_NAMESPACE_URI,"xml.xsd");
    addInternalResource(XmlUtil.XHTML_URI,"xhtml1-transitional.xsd");
    addInternalResource("http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd","xhtml1-strict.xsd");

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

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final File extResourceFolder = new File(FetchExtResourceAction.getExternalResourcesPath());

      if (extResourceFolder.exists()) {
        Runnable action = new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                final VirtualFile extResourceDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(extResourceFolder);
                if (extResourceDir != null) LocalFileSystem.getInstance().addRootToWatch(extResourceDir.getPath(), true);
              }
            });
          }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) action.run();
        else ApplicationManager.getApplication().invokeLater(action);
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
    addStdResource(resource, STANDARD_SCHEMAS + fileName);
  }

  public void addInternalResource(@NonNls String resource, @NonNls String version, @NonNls String fileName) {
    addStdResource(resource, version, STANDARD_SCHEMAS + fileName, getClass());
  }

  public void addStdResource(@NonNls String resource, @NonNls String fileName){
    addStdResource(resource, fileName, getClass());
  }

  public void addStdResource(String resource, String fileName, Class klass) {
    addStdResource(resource, DEFAULT_VERSION, fileName, klass);
  }

  public void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, Class klass) {
    final String file = getFile(fileName, klass);
    if (file != null) {
      final Map<String, String> map = getMap(myStdResources, version, true);
      assert map != null;
      map.put(resource, file);
    }
    else {
      LOG.info("Cannot find standard resource. filename:" + fileName + " klass=" + klass);
    }
  }

  @Nullable
  private static Map<String, String> getMap(@NotNull final Map<String, Map<String, String>> resources, @Nullable final String version,
                                            final boolean create) {
    Map<String, String> map = resources.get(version);
    if (map == null) {
      if (create) {
        map = new HashMap<String, String>();
        resources.put(version,map);
      } else if (version == null || !version.equals(DEFAULT_VERSION)) {
        map = resources.get(DEFAULT_VERSION);
      }
    }

    return map;
  }

  public String getResourceLocation(String url) {
    return getResourceLocation(url, DEFAULT_VERSION);
  }

  public String getResourceLocation(@NonNls String url, String version) {
    Map<String, String> map = getMap(myResources, version, false);
    String result = map != null ? map.get(url):null;

    if (result == null) {
      map = getMap(myStdResources, version, false);
      result = map != null ? map.get(url):null;
    }

    if (result == null) {
      result = url;
    }

    return result;
  }

  @Nullable
  public PsiFile getResourceLocation(@NotNull @NonNls final String url, @NotNull final PsiFile baseFile, final String version) {
    final XmlFile schema = XmlSchemaProvider.findSchema(url, baseFile);
    if (schema != null) {
      return schema;
    }
    final String location = getResourceLocation(url, version);
    return XmlUtil.findXmlFile(baseFile, location);
  }

  private final UserDataCache<CachedValue<MultiMap<String, VirtualFile>>, Project, Project> myNamespaces = new UserDataCache<CachedValue<MultiMap<String, VirtualFile>>, Project, Project>("external namespaces") {
    protected CachedValue<MultiMap<String, VirtualFile>> compute(final Project project, final Project p) {
      return PsiManager.getInstance(project).getCachedValuesManager().createCachedValue(new CachedValueProvider<MultiMap<String, VirtualFile>>() {
        public Result<MultiMap<String, VirtualFile>> compute() {
          final MultiMap<String, VirtualFile> map = new ConcurrentMultiMap<String, VirtualFile>();
          final Consumer<Map.Entry<String, String>> consumer = new Consumer<Map.Entry<String, String>>() {
            public void consume(final Map.Entry<String, String> entry) {
              @NonNls final String path = entry.getValue();
              if (path.endsWith(".xsd")) {
                final VirtualFile file = VfsUtil.findRelativeFile(path, null);
                if (file == null) {
                  return;
                }
                final String ns = XsdNamespaceBuilder.computeNamespace(file);
                if (ns == null) {
                  return;
                }
                map.putValue(ns, file);
              }
            }
          };
          processEntries(myStdResources, consumer);
          processEntries(myResources, consumer);
          return Result.createSingleDependency(map, ExternalResourceManagerImpl.this);
        }
      }, false);
    }
  };

  private final UserDataCache<CachedValue<MultiMap<String, String>>, Project, Project> myNamespacesByTagName =
      new UserDataCache<CachedValue<MultiMap<String, String>>, Project, Project>("external tag names") {
    protected CachedValue<MultiMap<String, String>> compute(final Project project, final Project p) {
      return PsiManager.getInstance(project).getCachedValuesManager().createCachedValue(new CachedValueProvider<MultiMap<String, String>>() {
        public Result<MultiMap<String, String>> compute() {
          final MultiMap<String, String> result = new ConcurrentMultiMap<String, String>();
          final Consumer<Map.Entry<String, String>> consumer = new Consumer<Map.Entry<String, String>>() {
            public void consume(final Map.Entry<String, String> entry) {
              @NonNls final String path = entry.getValue();
              if (path.endsWith(".xsd")) {
                final VirtualFile file = VfsUtil.findRelativeFile(path, null);
                if (file == null) {
                  return;
                }
                final String ns = XsdNamespaceBuilder.computeNamespace(file);
                if (ns == null) {
                  return;
                }
                final Collection<String> tagNames = XsdTagNameBuilder.computeTagNames(file);
                if (tagNames != null) {
                  for (String tagName : tagNames) {
                    result.putValue(tagName, ns);
                  }
                }
              }

            }
          };
          processEntries(myStdResources, consumer);
          processEntries(myResources, consumer);
          return Result.createSingleDependency(result, ExternalResourceManagerImpl.this);
        }
      }, false);
    }
  };

  private static void processEntries(final Map<String, Map<String, String>> resources, Consumer<Map.Entry<String, String>> consumer) {
    for (Map<String, String> map : resources.values()) {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        consumer.consume(entry);
      }
    }
  }

  public Set<String> getNamespacesByTagName(String tagName, Project project) {
    final MultiMap<String, String> value = myNamespacesByTagName.get(project, project).getValue();
    assert value != null;
    final Collection<String> strings = value.get(tagName);
    return strings == null ? Collections.<String>emptySet() : new HashSet<String>(strings);
  }

  public Collection<VirtualFile> getSchemaFiles(String namespace, Project project) {
    final MultiMap<String, VirtualFile> value = myNamespaces.get(project, project).getValue();
    assert value != null;
    return value.get(namespace);
  }

  public String[] getResourceUrls(FileType fileType, final boolean includeStandard) {
    return getResourceUrls(fileType, DEFAULT_VERSION, includeStandard);
  }

  public String[] getResourceUrls(final FileType fileType, @NonNls final String version, final boolean includeStandard) {
    final List<String> result = new LinkedList<String>();
    addResourcesFromMap(fileType, result,version, myResources);

    if (includeStandard) {
      addResourcesFromMap(fileType, result,version, myStdResources);
    }

    return result.toArray(new String[result.size()]);
  }

  private static void addResourcesFromMap(final FileType fileType, final List<String> result, String version, Map<String, Map<String, String>> resourcesMap) {
    Map<String, String> resources = getMap(resourcesMap, version, false);
    if (resources == null) return;
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
    addResource(url, DEFAULT_VERSION, location);
  }

  public void addResource(@NonNls String url, @NonNls String version, @NonNls String location) {
    final Map<String, String> map = getMap(myResources, version, true);
    assert map != null;
    map.put(url, location);
    myModificationCount++;
    fireExternalResourceChanged();
  }

  public void removeResource(String url) {
    removeResource(url, DEFAULT_VERSION);
  }

  public void removeResource(String url, String version) {
    Map<String, String> map = getMap(myResources, version, false);
    if (map != null) {
      map.remove(url);
      myModificationCount++;
      fireExternalResourceChanged();
    }
  }

  public String[] getAvailableUrls() {
    Set<String> urls = new HashSet<String>();
    for (Map<String, String> map : myResources.values()) {
      urls.addAll(map.keySet());
    }
    return urls.toArray(new String[urls.size()]);
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
    return myIgnoredResources.contains(url) || getImplicitNamespaceDescriptor(url) != null;
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
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }

  @NotNull
  public String getComponentName() {
    return "ExternalResourceManagerImpl";
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

  public void addImplicitNamespace(@NotNull final String ns, @NotNull XmlNSDescriptorImpl descriptor, Disposable parentDisposable) {
    myImplicitNamespaces.put(ns, descriptor);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myImplicitNamespaces.remove(ns);
      }
    });
  }

  @Nullable
  public XmlNSDescriptorImpl getImplicitNamespaceDescriptor(@NotNull final String ns) {
    return myImplicitNamespaces.get(ns);
  }
}
