package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.IdeaClassLoader;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;
import sun.reflect.Reflection;

import javax.swing.*;
import java.io.File;
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

  private static String[] ourArguments;
  private static String ourMainClass;
  private static String ourMethodName;

  private static PluginDescriptor[] ourPlugins;
  private static Map<String, String> ourPluginClasses;
  private static final String TOOLS_JAR = "tools.jar";
  private static final String EXTENSIONS_DIR = "ext";


  public PluginManager() {
  }

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, List classpathElements) {
    ourArguments = args;
    ourMainClass = mainClass;
    ourMethodName = methodName;

    final PluginManager pluginManager = new PluginManager();
    pluginManager.bootstrap(classpathElements);
  }

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  public static PluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      final PluginDescriptor[] pluginDescriptors = loadDescriptors();

      final Map<String, PluginDescriptor> idToDescriptorMap = new HashMap<String, PluginDescriptor>();
      for (int idx = 0; idx < pluginDescriptors.length; idx++) {
        final PluginDescriptor descriptor = pluginDescriptors[idx];
        idToDescriptorMap.put(descriptor.getId(), descriptor);
      }
      // sort descriptors according to plugin dependencies
      Arrays.sort(pluginDescriptors, getPluginDescriptorComparator(idToDescriptorMap));

      final Class callerClass = Reflection.getCallerClass(1);
      final ClassLoader parentLoader = callerClass.getClassLoader();
      for (int idx = 0; idx < pluginDescriptors.length; idx++) {
        final PluginDescriptor pluginDescriptor = pluginDescriptors[idx];
        final List classPath = pluginDescriptor.getClassPath();
        final String[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
        final ClassLoader[] parentLoaders = dependentPluginIds.length > 0? getParentLoaders(idToDescriptorMap, dependentPluginIds): new ClassLoader[] {parentLoader};
        final PluginClassLoader pluginClassLoader = createPluginClassLoader((File[])classPath.toArray(new File[classPath.size()]), pluginDescriptor.getName(), parentLoaders, pluginDescriptor.getPath());
        pluginDescriptor.setLoader(pluginClassLoader);
      }
      ourPlugins = pluginDescriptors;
    }
    return ourPlugins;
  }

  private static Comparator<PluginDescriptor> getPluginDescriptorComparator(Map<String, PluginDescriptor> idToDescriptorMap) {
    final Graph<String> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<String> builder = new DFSTBuilder<String>(graph);
    /*
    if (!builder.isAcyclic()) {
      final Pair<String,String> circularDependency = builder.getCircularDependency();
      throw new Exception("Cyclic dependencies between plugins are not allowed: \"" + circularDependency.getFirst() + "\" and \"" + circularDependency.getSecond() + "");
    }
    */
    final Comparator<String> idComparator = builder.comparator();
    return new Comparator<PluginDescriptor>() {
      public int compare(PluginDescriptor o1, PluginDescriptor o2) {
        return idComparator.compare(o1.getId(), o2.getId());
      }
    };
  }

  private static Graph<String> createPluginIdGraph(final Map<String, PluginDescriptor> idToDescriptorMap) {
    final String[] ids = idToDescriptorMap.keySet().toArray(new String[idToDescriptorMap.size()]);
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return Arrays.asList(ids);
      }

      public Iterator<String> getIn(String pluginId) {
        final PluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        return Arrays.asList(descriptor.getDependentPluginIds()).iterator();
      }
    }));
  }

  private static ClassLoader[] getParentLoaders(Map<String, PluginDescriptor> idToDescriptorMap, String[] pluginIds) {
    final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
    for (int idx = 0; idx < pluginIds.length; idx++) {
      final String id = pluginIds[idx];
      PluginDescriptor pluginDescriptor = idToDescriptorMap.get(id);
      if (pluginDescriptor == null) {
        getLogger().assertTrue(false, "Plugin not found: " + id);
      }
      final PluginClassLoader loader = pluginDescriptor.getLoader();
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
    final Map<String, PluginDescriptor> idToDescriptorMap = new HashMap<String, PluginDescriptor>();
    final StringBuffer message = new StringBuffer();
    boolean pluginsWithoutIdFound = false;
    for (Iterator it = result.iterator(); it.hasNext();) {
      final PluginDescriptor descriptor = (PluginDescriptor)it.next();
      String id = descriptor.getId();
      if (id == null || id.length() == 0) {
        pluginsWithoutIdFound = true;
        it.remove();
      }
      else if (idToDescriptorMap.containsKey(id)) {
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
    for (Iterator it = result.iterator(); it.hasNext();) {
      final PluginDescriptor pluginDescriptor = (PluginDescriptor)it.next();
      final String[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      for (int idx = 0; idx < dependentPluginIds.length; idx++) {
        final String dependentPluginId = dependentPluginIds[idx];
        if (!idToDescriptorMap.containsKey(dependentPluginId)) {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append("Plugin \"");
          message.append(pluginDescriptor.getId());
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
    return null;
  }

  private static void loadDescriptors(String pluginsPath, List<PluginDescriptor> result) {
    final File pluginsHome = new File(pluginsPath);
    final File[] files = pluginsHome.listFiles();
    if (files!= null && files.length > 0) {
      for (int i = 0; i < files.length; i++) {
        final PluginDescriptor descriptor = loadDescriptor(files[i]);
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
        for (int i = 0; i < files.length; i++) {
          final File f = files[i];
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

  protected void bootstrap(List classpathElements) {
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

    try {
      IdeaClassLoader newClassLoader = new IdeaClassLoader(classpathElements, null);

      // prepare plugins
      if (!isLoadingOfExternalPluginsDisabled()) {
        PluginInstaller.initPluginClasses ();
        StartupActionScriptManager.executeActionScript();
      }

      Thread.currentThread().setContextClassLoader(newClassLoader);

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
      if (logger == null)
        e.printStackTrace(System.err);
      else
        logger.error(e);
    }
  }

  private static PluginClassLoader createPluginClassLoader(final File[] classPath, final String pluginName, final ClassLoader[] parentLoaders, File pluginRoot) {
    try {
      final List urls = new ArrayList(classPath.length);
      for (int idx = 0; idx < classPath.length; idx++) {
        urls.add(classPath[idx].toURL());
      }
      return new PluginClassLoader(urls, parentLoaders, pluginName, pluginRoot);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return null;
  }


  private void addParentClasspath(List aClasspathElements) throws MalformedURLException {
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

  private void addIDEALibraries(List classpathElements) {
    final String ideaHomePath = PathManager.getHomePath();
    addJreLibraries(ideaHomePath, classpathElements);
    addAllFromLibFolder(ideaHomePath, classpathElements);
  }

  public static void addJreLibraries(final String aIdeaHomePath, List classPath) {
    try {
      final String libPath = aIdeaHomePath + File.separator + "jre" + File.separator + "lib" + File.separator;
      File toolsFile = new File(libPath + TOOLS_JAR);
      if (!toolsFile.exists()) {
        toolsFile = null;
      }
      if (toolsFile != null) {
        classPath.add(toolsFile.toURL());
      }
      final File[] extFiles = new File(libPath + EXTENSIONS_DIR).listFiles();
      if (extFiles != null) {
        for (int idx = 0; idx < extFiles.length; idx++) {
          File extFile = extFiles[idx];
          if (isJarOrZip(extFile)) {
            classPath.add(extFile.toURL());
          }
        }
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);

    }
  }

  public static void addAllFromLibFolder(final String aFolderPath, List classPath) {
    try {
      final Class aClass = PluginManager.class;
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

  private static void addLibraries(List classPath, File fromDir, final URL selfRootUrl) throws MalformedURLException {
    final File[] files = fromDir.listFiles();
    if (files != null) {
      for (int idx = 0; idx < files.length; idx++) {
        final File file = files[idx];
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

  private void addAdditionalClassPath(List classPath) {
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

  public static boolean isPluginInstalled (String name) {
    return (getPlugin(name) != null);
  }

  public static PluginDescriptor getPlugin (String name) {
    final PluginDescriptor[] plugins = getPlugins();
    for (int i = 0; i < plugins.length; i++) {
      final PluginDescriptor plugin = plugins[i];
      if (name.equals(plugin.getName())) {
        return plugin;
      }
    }
    return null;
  }

  public static void addPluginClass (String className, String pluginName) {
    if (ourPluginClasses == null) {
      ourPluginClasses = new HashMap<String, String> ();
    }
    ourPluginClasses.put(className, pluginName);
  }

  public static boolean isPluginClass (String className) {
    return getPluginByClassName(className) != null;
  }

  public static String getPluginByClassName (String className) {
    return ourPluginClasses != null? ourPluginClasses.get(className) : null;
  }
}
