// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.lw.AsmClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.UIManager;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.PROJECT)
public final class LoaderFactory implements Disposable {
  private static final Logger LOG = Logger.getInstance(LoaderFactory.class);

  private final Project myProject;
  private final ConcurrentMap<Module, ClassLoader> myModule2ClassLoader;
  private final ConcurrentMap<Module, InstrumentationClassFinder> myModule2ClassFinder;
  private final AtomicReference<InstrumentationClassFinder> myPlatformClassFinder = new AtomicReference<>();
  private final MessageBusConnection myConnection;
  private ClassLoader myProjectClassLoader = null;

  public static LoaderFactory getInstance(final Project project) {
    return project.getService(LoaderFactory.class);
  }

  public LoaderFactory(final Project project) {
    myProject = project;
    myModule2ClassLoader = CollectionFactory.createConcurrentWeakMap();
    myModule2ClassFinder = CollectionFactory.createConcurrentWeakMap();
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(final @NotNull ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          clearClassLoaderCache();
        });
      }
    });
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myModule2ClassLoader.clear();
    releaseClassFinders();
  }

  public @NotNull ClassLoader getLoader(@NotNull VirtualFile formFile) {
    var module = ModuleUtilCore.findModuleForFile(formFile, myProject);
    return module != null ? getLoader(module) : getClass().getClassLoader();
  }

  public ClassLoader getLoader(@NotNull Module module) {
    var cachedLoader = myModule2ClassLoader.get(module);
    if (cachedLoader != null) return cachedLoader;

    var runClasspath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString();
    var classLoader = createClassLoader(runClasspath, module.getName());
    myModule2ClassLoader.put(module, classLoader);
    return classLoader;
  }

  public @NotNull ClassLoader getProjectClassLoader() {
    if (myProjectClassLoader == null) {
      var runClasspath = OrderEnumerator.orderEntries(myProject).withoutSdk().getPathsList().getPathsString();
      myProjectClassLoader = createClassLoader(runClasspath, "<project>");
    }
    return myProjectClassLoader;
  }

  /**
   * Parses a form file. Component properties are discovered by reading bytecode, so that opening a form
   * never loads, initializes or introspects the component classes it names (IDEA-392515).
   */
  public @NotNull LwRootContainer readRootContainer(@NotNull VirtualFile formFile, @NotNull String text) throws Exception {
    var classFinder = getClassFinder(formFile);
    // neither InstrumentationClassFinder nor AsmClassPropertiesProvider is thread safe, and a finder is shared by
    // every form of its module - the forms of other modules have a finder of their own and do not wait here
    synchronized (classFinder) {
      try {
        return Utils.getRootContainer(text, new AsmClassPropertiesProvider(classFinder));
      }
      finally {
        // between two reads the finder must not keep the jars of the module open, nor answer with the classes of a
        // previous build; only its platform roots are worth keeping, see #getClassFinder
        classFinder.releaseClasspathResources();
      }
    }
  }

  private @NotNull InstrumentationClassFinder getClassFinder(@NotNull VirtualFile formFile) {
    var module = ModuleUtilCore.findModuleForFile(formFile, myProject);
    return module != null ? getClassFinder(module) : getPlatformClassFinder();
  }

  /**
   * Only reading a form is guarded, never the lookup of the finder to read it with - a lock must not be held while
   * the project model is read, and the roots below are read without one.
   */
  private @NotNull InstrumentationClassFinder getClassFinder(@NotNull Module module) {
    var cachedFinder = myModule2ClassFinder.get(module);
    if (cachedFinder != null) return cachedFinder;

    var runClasspath = OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathsString() +
                       File.pathSeparator + spacerJarPath();
    var platformUrls = getPlatformUrls(module);
    if (platformUrls == null) {
      // The module has no JDK of its own - it may have none configured, or the project may not have been
      // loaded at all because it is not trusted. Fall back to the JDK the IDE runs on, so that at least the
      // standard Swing components still resolve. This is what the design time class loader has always done
      // through its parent, see #createClassLoader.
      platformUrls = createPlatformUrls(System.getProperty("java.home"));
    }
    var classFinder = createClassFinder(platformUrls, runClasspath);
    // a finder opens a root only when it is asked for a class, so one that loses this race has nothing to release
    var raceWinner = myModule2ClassFinder.putIfAbsent(module, classFinder);
    return raceWinner != null ? raceWinner : classFinder;
  }

  /**
   * Fallback for forms outside any module: only the classes of the JDK the IDE itself runs on are visible,
   * which is enough for the standard Swing components.
   */
  private @NotNull InstrumentationClassFinder getPlatformClassFinder() {
    return myPlatformClassFinder.updateAndGet(
      finder -> finder != null ? finder
                               : createClassFinder(createPlatformUrls(System.getProperty("java.home")), spacerJarPath()));
  }

  private static @NotNull String spacerJarPath() {
    var jar = PathManager.getJarForClass(Spacer.class);
    return jar != null ? jar.toString() : "";
  }

  public static @NotNull InstrumentationClassFinder createClassFinder(URL @Nullable [] platformUrls, final @NotNull String classPath) {
    final ArrayList<URL> urls = new ArrayList<>();
    for (StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens();) {
      final String s = tokenizer.nextToken();
      try {
        urls.add(new File(s).toURI().toURL());
      }
      catch (Exception exc) {
        throw new RuntimeException(exc);
      }
    }
    URL[] zero = new URL[0];
    return new InstrumentationClassFinder(platformUrls == null ? zero : platformUrls, urls.toArray(zero));
  }

  /**
   * Classes of JDK 9 and later are not reachable through the classpath, they need an explicit {@code jrt:} root.
   */
  public static URL @Nullable [] getPlatformUrls(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || sdk.getHomePath() == null || !JavaSdk.getInstance().isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_9)) {
      return null;
    }
    return createPlatformUrls(sdk.getHomePath());
  }

  private static URL @Nullable [] createPlatformUrls(@Nullable String jdkHomePath) {
    if (jdkHomePath == null) {
      return null;
    }
    try {
      return new URL[]{InstrumentationClassFinder.createJDKPlatformUrl(jdkHomePath)};
    }
    catch (MalformedURLException e) {
      LOG.warn("Cannot use " + jdkHomePath + " as a platform class root", e);
      return null;
    }
  }

  private static ClassLoader createClassLoader(String runClasspath, String moduleName) {
    var files = new ArrayList<Path>();
    for (var tokenizer = new StringTokenizer(runClasspath, File.pathSeparator); tokenizer.hasMoreTokens(); ) {
      files.add(Path.of(tokenizer.nextToken()));
    }
    files.add(PathManager.getJarForClass(Spacer.class));
    return new DesignTimeClassLoader(files, LoaderFactory.class.getClassLoader(), moduleName);
  }

  public void clearClassLoaderCache() {
    // clear classes with invalid classloader from UIManager cache
    var uiDefaults = UIManager.getDefaults();
    for (var it = uiDefaults.keySet().iterator(); it.hasNext(); ) {
      var key = it.next();
      var value = uiDefaults.get(key);
      if (value instanceof Class) {
        var loader = ((Class<?>)value).getClassLoader();
        if (loader instanceof DesignTimeClassLoader) {
          it.remove();
        }
      }
    }
    myModule2ClassLoader.clear();
    myProjectClassLoader = null;
    releaseClassFinders();
  }

  /**
   * A cached finder is only good for the roots it was built from, and it holds the {@code jrt:} file system of the
   * module SDK open, so it has to go when the roots change. The jars of the classpath are already released after every
   * read, see {@link #readRootContainer}.
   */
  private void releaseClassFinders() {
    var released = new ArrayList<InstrumentationClassFinder>();
    // remove one by one rather than clear: a finder must never be dropped from the map without being released
    for (var module : new ArrayList<>(myModule2ClassFinder.keySet())) {
      var finder = myModule2ClassFinder.remove(module);
      if (finder != null) {
        released.add(finder);
      }
    }
    var platformClassFinder = myPlatformClassFinder.getAndSet(null);
    if (platformClassFinder != null) {
      released.add(platformClassFinder);
    }
    for (var finder : released) {
      // do not pull the jars away from a form that is being read right now - the finder is the lock of its module,
      // see #readRootContainer
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (finder) {
        finder.releaseResources();
      }
    }
  }

  private static final class DesignTimeClassLoader extends UrlClassLoader {
    private static final boolean isParallelCapable = registerAsParallelCapable();

    private final String myModuleName;

    DesignTimeClassLoader(List<Path> files, ClassLoader parent, String moduleName) {
      super(build().files(files).allowLock(false).parent(parent), isParallelCapable);
      myModuleName = moduleName;
    }

    @Override
    public String toString() {
      return "DesignTimeClassLoader:" + myModuleName;
    }
  }
}
