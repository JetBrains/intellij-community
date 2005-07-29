package com.intellij.ide.plugins;

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.InspectionMain;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.execution.JUnitPatcher;
import com.intellij.ide.plugins.cl.IdeaClassLoader;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffApplication;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import sun.reflect.Reflection;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author mike
 */
public class PluginManager {
  //Logger is lasy-initialized in order not to use it outside the appClassLoader
  private static Logger ourLogger = null;

  private static Logger getLogger() {
    if (ourLogger == null) {
      ourLogger = Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
    }
    return ourLogger;
  }

  // these fields are accessed via Reflection, so their names must not be changed by the obfuscator
  // do not make them private cause in this case they will be scrambled
  static String[] ourArguments;
  static String ourMainClass;
  static String ourMethodName;

  private static PluginDescriptor[] ourPlugins;
  private static Map<String, PluginId> ourPluginClasses;

  public PluginManager() {
  }

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList<URL>());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, List<URL> classpathElements) {
    ourArguments = args;
    ourMainClass = mainClass;
    ourMethodName = methodName;

    final PluginManager pluginManager = new PluginManager();
    pluginManager.bootstrap(classpathElements);
  }

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  public synchronized static PluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      initializePlugins();
    }
    return ourPlugins;
  }

  private static void initializePlugins() {
    if (!shouldLoadPlugins()) {
      ourPlugins = new PluginDescriptor[0];
      return;
    }

    configureExtensions();

    final PluginDescriptor[] pluginDescriptors = loadDescriptors();

    final Map<PluginId, PluginDescriptor> idToDescriptorMap = new HashMap<PluginId, PluginDescriptor>();
    for (final PluginDescriptor descriptor : pluginDescriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }
    // sort descriptors according to plugin dependencies
    Arrays.sort(pluginDescriptors, getPluginDescriptorComparator(idToDescriptorMap));

    final Class callerClass = Reflection.getCallerClass(1);
    final ClassLoader parentLoader = callerClass.getClassLoader();
    for (final PluginDescriptor pluginDescriptor : pluginDescriptors) {
      final List<File> classPath = pluginDescriptor.getClassPath();
      final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      final ClassLoader[] parentLoaders = dependentPluginIds.length > 0
                                          ? getParentLoaders(idToDescriptorMap, dependentPluginIds)
                                          : new ClassLoader[]{parentLoader};
      final IdeaClassLoader pluginClassLoader = createPluginClassLoader(classPath.toArray(new File[classPath.size()]),
                                                                        pluginDescriptor.getPluginId(), parentLoaders,
                                                                        pluginDescriptor.getPath());
      pluginDescriptor.setLoader(pluginClassLoader);
      pluginDescriptor.registerExtensions();
    }
    ourPlugins = pluginDescriptors;
  }

  private static void configureExtensions() {
    Extensions.setLogProvider(new IdeaLogProvider());
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    Extensions.registerAreaClass("IDEA_MODULE", "IDEA_PROJECT");

    Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.COMPONENT, ComponentDescriptor.class.getName());

    Extensions.getRootArea().getExtensionPoint(Extensions.AREA_LISTENER_EXTENSION_POINT).registerExtension(new AreaListener() {
      public void areaCreated(String areaClass, AreaInstance areaInstance) {
        if ("IDEA_PROJECT".equals(areaClass) || "IDEA_MODULE".equals(areaClass)) {
          Extensions.getArea(areaInstance).registerExtensionPoint(ExtensionPoints.COMPONENT, ComponentDescriptor.class.getName());
        }
      }

      public void areaDisposing(String areaClass, AreaInstance areaInstance) {
      }
    }, LoadingOrder.FIRST);

    Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.APPLICATION_STARTER, ApplicationStarter.class.getName());
    Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.ERROR_HANDLER, ErrorReportSubmitter.class.getName());
    Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.JUNIT_PATCHER, JUnitPatcher.class.getName());
    Extensions.getRootArea().getExtensionPoint(ExtensionPoints.ERROR_HANDLER).registerExtension(new ITNReporter());
    Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER).registerExtension(new InspectionMain());
    Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER).registerExtension(new DiffApplication());
  }

  public static boolean shouldLoadPlugins() {
    try {
      // no plugins during bootstrap
      Class.forName("com.intellij.openapi.extensions.Extensions");
    }
    catch (ClassNotFoundException e) {
      return false;
    }
    final String loadPlugins = System.getProperty("idea.load.plugins");
    return loadPlugins == null || "true".equals(loadPlugins);
  }

  public static boolean shouldLoadPlugin(PluginDescriptor descriptor) {
    final String loadPluginCategory = System.getProperty("idea.load.plugins.category");
    return loadPluginCategory == null || loadPluginCategory.equals(descriptor.getCategory());
  }


  private static Comparator<PluginDescriptor> getPluginDescriptorComparator(Map<PluginId,PluginDescriptor> idToDescriptorMap) {
    final Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<PluginId> builder = new DFSTBuilder<PluginId>(graph);
    /*
    if (!builder.isAcyclic()) {
      final Pair<String,String> circularDependency = builder.getCircularDependency();
      throw new Exception("Cyclic dependencies between plugins are not allowed: \"" + circularDependency.getFirst() + "\" and \"" + circularDependency.getSecond() + "");
    }
    */
    final Comparator<PluginId> idComparator = builder.comparator();
    return new Comparator<PluginDescriptor>() {
      public int compare(PluginDescriptor o1, PluginDescriptor o2) {
        return idComparator.compare(o1.getPluginId(), o2.getPluginId());
      }
    };
  }

  private static Graph<PluginId> createPluginIdGraph(final Map<PluginId,PluginDescriptor> idToDescriptorMap) {
    final PluginId[] ids = idToDescriptorMap.keySet().toArray(new PluginId[idToDescriptorMap.size()]);
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<PluginId>() {
      public Collection<PluginId> getNodes() {
        return Arrays.asList(ids);
      }

      public Iterator<PluginId> getIn(PluginId pluginId) {
        final PluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        return Arrays.asList(descriptor.getDependentPluginIds()).iterator();
      }
    }));
  }

  private static ClassLoader[] getParentLoaders(Map<PluginId,PluginDescriptor> idToDescriptorMap, PluginId[] pluginIds) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new ClassLoader[0];
    final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
    for (final PluginId id : pluginIds) {
      PluginDescriptor pluginDescriptor = idToDescriptorMap.get(id);
      if (pluginDescriptor == null) {
        getLogger().assertTrue(false, "Plugin not found: " + id);
      }
      final ClassLoader loader = pluginDescriptor.getLoader();
      if (loader == null) {
        getLogger().assertTrue(false, "Plugin class loader should be initialized for plugin " + id);
      }
      classLoaders.add(loader);
    }
    return classLoaders.toArray(new ClassLoader[classLoaders.size()]);
  }

  /**
   * Called via reflection
   */
  protected static void start() {
    try {
      ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
        public void uncaughtException(Thread t, Throwable e) {
          getLogger().error(e);
        }
      };

      Runnable runnable = new Runnable() {
        public void run() {
          try {
            Class aClass = Class.forName(ourMainClass);
            final Method method = aClass.getDeclaredMethod(ourMethodName, new Class[]{(ArrayUtil.EMPTY_STRING_ARRAY).getClass()});
            method.setAccessible(true);

            method.invoke(null, new Object[]{ourArguments});
          }
          catch (Exception e) {
            e.printStackTrace();
            getLogger().error(e);
          }
        }
      };

      new Thread(threadGroup, runnable, "Idea Main Thread").start();
    }
    catch (Exception e) {
      getLogger().error(e);
    }
  }

  private static PluginDescriptor[] loadDescriptors() {
    if (isLoadingOfExternalPluginsDisabled()) {
      return PluginDescriptor.EMPTY_ARRAY;
    }

    final List<PluginDescriptor> result = new ArrayList<PluginDescriptor>();

    loadDescriptors(PathManager.getPluginsPath(), result);
    loadDescriptors(PathManager.getPreinstalledPluginsPath(), result);

    String errorMessage = filterBadPlugins(result);

    PluginDescriptor[] pluginDescriptors = result.toArray(new PluginDescriptor[result.size()]);
    try {
      Arrays.sort(pluginDescriptors, new PluginDescriptorComparator(pluginDescriptors));
    }
    catch (Exception e) {
      errorMessage = "Error loading plugins:\n" + e.getMessage() + "\nPlugins were not loaded.\nCorrect the above error and restart IDEA.";
      pluginDescriptors = PluginDescriptor.EMPTY_ARRAY;
    }
    if (errorMessage != null) {
      JOptionPane.showMessageDialog(null, errorMessage, "Plugin Error", JOptionPane.ERROR_MESSAGE);
    }
    return pluginDescriptors;
  }

  private static String filterBadPlugins(List<PluginDescriptor> result) {
    final Map<PluginId, PluginDescriptor> idToDescriptorMap = new HashMap<PluginId, PluginDescriptor>();
    final StringBuffer message = new StringBuffer();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<PluginDescriptor> it = result.iterator(); it.hasNext();) {
      final PluginDescriptor descriptor = it.next();
      final PluginId id = descriptor.getPluginId();
      if (idToDescriptorMap.containsKey(id)) {
        if (message.length() > 0) {
          message.append("\n");
        }
        message.append("Duplicate plugin id: ");
        message.append(id);
        it.remove();
      }
      else {
        idToDescriptorMap.put(id, descriptor);
      }
    }
    for (Iterator<PluginDescriptor> it = result.iterator(); it.hasNext();) {
      final PluginDescriptor pluginDescriptor = it.next();
      final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      for (final PluginId dependentPluginId : dependentPluginIds) {
        if (!idToDescriptorMap.containsKey(dependentPluginId)) {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append("Plugin \"");
          message.append(pluginDescriptor.getPluginId());
          message.append("\" was not loaded: required plugin \"");
          message.append(dependentPluginId);
          message.append("\" not found.");
          it.remove();
          break;
        }
      }
    }
    if (pluginsWithoutIdFound) {
      if (message.length() > 0) {
        message.append("\n");
      }
      message.append("There were plugins without id found, all such plugins were skipped.");
    }
    if (message.length() > 0) {
      message.insert(0, "Problems found loading plugins:\n");
      return message.toString();
    }
    for (Iterator<PluginDescriptor> iterator = result.iterator(); iterator.hasNext();) {
      PluginDescriptor descriptor = iterator.next();
      if (!shouldLoadPlugins() || !shouldLoadPlugin(descriptor)) {
        iterator.remove();
      }
    }
    return null;
  }

  private static void loadDescriptors(String pluginsPath, List<PluginDescriptor> result) {
    final File pluginsHome = new File(pluginsPath);
    final File[] files = pluginsHome.listFiles();
    if (files!= null) {
      for (File file : files) {
        final PluginDescriptor descriptor = loadDescriptor(file);
        if (descriptor != null && !result.contains(descriptor)) {
          result.add(descriptor);
        }
      }
    }
  }

  private static PluginDescriptor loadDescriptor(final File file) {
    PluginDescriptor descriptor = null;

    if (file.isDirectory()) {
      File descriptorFile = new File(file, "META-INF" + File.separator + "plugin.xml");
      if (descriptorFile.exists()) {
        descriptor = new PluginDescriptor(file);

        try {
          descriptor.readExternal(JDOMUtil.loadDocument(descriptorFile).getRootElement());
        }
        catch (Exception e) {
          System.err.println("Cannot load: " + descriptorFile.getAbsolutePath());
          e.printStackTrace();
        }
      }
      else {
        File libDir = new File(file, "lib");
        if (!libDir.isDirectory()) {
          return null;
        }
        final File[] files = libDir.listFiles();
        if (files == null || files.length == 0) {
          return null;
        }
        for (final File f : files) {
          if (!f.isFile()) {
            continue;
          }
          final String lowercasedName = f.getName().toLowerCase();
          if (lowercasedName.endsWith(".jar") || lowercasedName.endsWith(".zip")) {
            PluginDescriptor descriptor1 = loadDescriptorFromJar(f);
            if (descriptor1 != null) {
              if (descriptor != null) {
                getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
                return null;
              }
              descriptor = descriptor1;
              descriptor.setPath(file);
            }
          }
        }
      }
    }
    else if (file.getName().endsWith(".jar")) {
      descriptor = loadDescriptorFromJar(file);
    }

    return descriptor;
  }

  private static PluginDescriptor loadDescriptorFromJar(File file) {
    PluginDescriptor descriptor = null;
    try {
      ZipFile zipFile = new ZipFile(file);

      try {
        final ZipEntry entry = zipFile.getEntry("META-INF/plugin.xml");
        if (entry != null) {
          descriptor = new PluginDescriptor(file);

          final InputStream inputStream = zipFile.getInputStream(entry);
          final Element rootElement = JDOMUtil.loadDocument(inputStream).getRootElement();
          inputStream.close();
          descriptor.readExternal(rootElement);
        }
      }
      finally {
        zipFile.close();
      }
    }
    catch (Exception e) {
      getLogger().info("Cannot load " + file, e);
    }
    return descriptor;
  }

  protected void bootstrap(List<URL> classpathElements) {
    IdeaClassLoader newClassLoader = initClassloader(classpathElements);
    try {
      final Class mainClass = Class.forName(getClass().getName(), true, newClassLoader);
      Field field = mainClass.getDeclaredField("ourMainClass");
      field.setAccessible(true);
      field.set(null, ourMainClass);

      field = mainClass.getDeclaredField("ourMethodName");
      field.setAccessible(true);
      field.set(null, ourMethodName);

      field = mainClass.getDeclaredField("ourArguments");
      field.setAccessible(true);
      field.set(null, ourArguments);

      final Method startMethod = mainClass.getDeclaredMethod("start", new Class[]{});
      startMethod.setAccessible(true);
      startMethod.invoke(null, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
  }

  public IdeaClassLoader initClassloader(final List<URL> classpathElements) {
    PathManager.loadProperties();

    try {
      addParentClasspath(classpathElements);
      addIDEALibraries(classpathElements);
      addAdditionalClassPath(classpathElements);
    }
    catch (IllegalArgumentException e) {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), "Error", JOptionPane.INFORMATION_MESSAGE);
      System.exit(1);
    }
    catch (MalformedURLException e) {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), "Error", JOptionPane.INFORMATION_MESSAGE);
      System.exit(1);
    }

    IdeaClassLoader newClassLoader = null;
    try {
      newClassLoader = new IdeaClassLoader(classpathElements, null);

      // prepare plugins
      if (!isLoadingOfExternalPluginsDisabled()) {
        PluginInstaller.initPluginClasses ();
        StartupActionScriptManager.executeActionScript();
      }

      Thread.currentThread().setContextClassLoader(newClassLoader);

    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
    return newClassLoader;
  }

  private static IdeaClassLoader createPluginClassLoader(final File[] classPath, final PluginId pluginId, final ClassLoader[] parentLoaders, File pluginRoot) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    try {
      final List<URL> urls = new ArrayList<URL>(classPath.length);
      for (File aClassPath : classPath) {
        final File file = aClassPath.getCanonicalFile(); // it is critical not to have "." and ".." in classpath elements
        urls.add(file.toURL());
      }
      return new PluginClassLoader(urls, parentLoaders, pluginId, pluginRoot);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  private void addParentClasspath(List<URL> aClasspathElements) throws MalformedURLException {
    final ClassLoader loader = getClass().getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader urlClassLoader = (URLClassLoader)loader;
      aClasspathElements.addAll(Arrays.asList(urlClassLoader.getURLs()));
    }
    else {
      try {
        Class antClassLoaderClass = Class.forName("org.apache.tools.ant.AntClassLoader");
        if (antClassLoaderClass.isInstance(loader)) {
          final String classpath = (String)antClassLoaderClass.getDeclaredMethod("getClasspath", new Class[0]).invoke(loader, ArrayUtil.EMPTY_OBJECT_ARRAY);
          final StringTokenizer tokenizer = new StringTokenizer(classpath, File.separator, false);
          while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            aClasspathElements.add(new File(token).toURL());
          }
        }
        else {
          getLogger().error("Unknown classloader: " + loader.getClass().getName());
        }
      }
      catch (ClassCastException e) {
        getLogger().error("Unknown classloader: " + e.getMessage());
      }
      catch (ClassNotFoundException e) {
        getLogger().error("Unknown classloader: " + loader.getClass().getName());
      }
      catch (NoSuchMethodException e) {
        getLogger().error("Unknown classloader: " + e.getMessage());
      }
      catch (IllegalAccessException e) {
        getLogger().error("Unknown classloader: " + e.getMessage());
      }
      catch (InvocationTargetException e) {
        getLogger().error("Unknown classloader: " + e.getMessage());
      }
    }
  }

  private void addIDEALibraries(List<URL> classpathElements) {
    final String ideaHomePath = PathManager.getHomePath();
    addAllFromLibFolder(ideaHomePath, classpathElements);
  }

  public static void addAllFromLibFolder(final String aFolderPath, List<URL> classPath) {
    try {
      final Class<PluginManager> aClass = PluginManager.class;
      final String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");

      final URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURL();
      classPath.add(selfRootUrl);

      final File libFolder = new File(aFolderPath + File.separator + "lib");
      addLibraries(classPath, libFolder, selfRootUrl);

      final File antLib = new File(new File(libFolder, "ant"), "lib");
      addLibraries(classPath, antLib, selfRootUrl);
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static void addLibraries(List<URL> classPath, File fromDir, final URL selfRootUrl) throws MalformedURLException {
    final File[] files = fromDir.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (!isJarOrZip(file)) {
          continue;
        }
        final URL url = file.toURL();
        if (selfRootUrl.equals(url)) {
          continue;
        }
        classPath.add(url);
      }
    }
  }

  private static boolean isJarOrZip(File file) {
    if (file.isDirectory()) {
      return false;
    }
    final String name = file.getName().toLowerCase();
    return name.endsWith(".jar") || name.endsWith(".zip");
  }

  private void addAdditionalClassPath(List<URL> classPath) {
    try {
      final StringTokenizer tokenizer = new StringTokenizer(System.getProperty("idea.additional.classpath", ""), File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classPath.add(new File(pathItem).toURL());
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static boolean isLoadingOfExternalPluginsDisabled() {
    return !"true".equalsIgnoreCase(System.getProperty("idea.plugins.load", "true"));
  }

  public static boolean isPluginInstalled (PluginId id) {
    return (getPlugin(id) != null);
  }

  public static PluginDescriptor getPlugin (PluginId id) {
    final PluginDescriptor[] plugins = getPlugins();
    for (final PluginDescriptor plugin : plugins) {
      if (Comparing.equal(id, plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public static void addPluginClass (String className, PluginId pluginId) {
    if (ourPluginClasses == null) {
      ourPluginClasses = new HashMap<String, PluginId> ();
    }
    ourPluginClasses.put(className, pluginId);
  }

  public static boolean isPluginClass (String className) {
    return getPluginByClassName(className) != null;
  }

  @Nullable
  public static PluginId getPluginByClassName (String className) {
    return ourPluginClasses != null? ourPluginClasses.get(className) : null;
  }

  private static class IdeaLogProvider implements LogProvider {
    public void error(String message) {
      getLogger().error(message);
    }

    public void error(String message, Throwable t) {
      getLogger().error(message, t);
    }

    public void error(Throwable t) {
      getLogger().error(t);
    }

    public void warn(String message) {
      getLogger().info(message);
    }

    public void warn(String message, Throwable t) {
      getLogger().info(message, t);
    }

    public void warn(Throwable t) {
      getLogger().info(t);
    }
  }
}
