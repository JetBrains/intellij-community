package com.intellij.compiler.ant;

import com.intellij.application.options.PathMacros;
import com.intellij.compiler.JavacSettings;
import com.intellij.compiler.ant.taskdefs.FileSet;
import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.j2ee.appServerIntegrations.impl.ApplicationServersManagerImpl;
import com.intellij.j2ee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
// todo: move path variables properties and jdk home properties into te generated property file
public class BuildProperties extends CompositeGenerator {
  public static final String TARGET_ALL = "all";
  public static final String TARGET_CLEAN = "clean";
  public static final String TARGET_INIT = "init";
  public static final String DEFAULT_TARGET = TARGET_ALL;

  public static final String PROPERTY_COMPILER_NAME = "compiler.name";
  public static final String PROPERTY_COMPILER_ADDITIONAL_ARGS = "compiler.args";
  public static final String PROPERTY_COMPILER_MAX_MEMORY = "compiler.max.memory";
  public static final String PROPERTY_COMPILER_EXCLUDES = "compiler.excluded";
  public static final String PROPERTY_COMPILER_RESOURCE_PATTERNS = "compiler.resources";
  public static final String PROPERTY_COMPILER_GENERATE_DEBUG_INFO = "compiler.debug";
  public static final String PROPERTY_COMPILER_GENERATE_NO_WARNINGS = "compiler.generate.no.warnings";
  public static final String PROPERTY_PROJECT_JDK_HOME = "project.jdk.home";
  public static final String PROPERTY_PROJECT_JDK_CLASSPATH = "project.jdk.classpath";
  public static final String PROPERTY_SKIP_TESTS = "skip.tests";

  public BuildProperties(Project project, final GenerationOptions genOptions) {
    add(new Property(getPropertyFileName(project)));

    add(new Comment("Uncomment the following property if no tests compilation is needed", new Property(PROPERTY_SKIP_TESTS, "true")));
    final JavacSettings javacSettings = JavacSettings.getInstance(project);
    if (genOptions.enableFormCompiler) {
      add(new Comment("The task requires the following libraries from IntelliJ IDEA distribution:"), 1);
      add(new Comment("  javac2.jar; jdom.jar; bcel.jar"));
      add(new Tag("taskdef", new Pair[] {
        new Pair("name", "javac2"),
        new Pair("classname", "com.intellij.uiDesigner.ant.Javac2"),
      }));
    }

    add(new Comment("Compiler options"), 1);
    add(new Property(PROPERTY_COMPILER_GENERATE_DEBUG_INFO, javacSettings.DEBUGGING_INFO? "on" : "off"), 1);
    add(new Property(PROPERTY_COMPILER_GENERATE_NO_WARNINGS, javacSettings.GENERATE_NO_WARNINGS? "on" : "off"));
    add(new Property(PROPERTY_COMPILER_ADDITIONAL_ARGS, javacSettings.ADDITIONAL_OPTIONS_STRING));
    add(new Property(PROPERTY_COMPILER_MAX_MEMORY, Integer.toString(javacSettings.MAXIMUM_HEAP_SIZE) + "m"));

    if (CompilerExcludes.isAvailable(project)) {
      add(new CompilerExcludes(project, genOptions));
    }

    add(new CompilerResourcePatterns(project));

    createJdkGenerators(project);

    LibraryDefinitionsGeneratorFactory factory = new LibraryDefinitionsGeneratorFactory((ProjectEx)project, genOptions);

    final Generator projectLibs = factory.create(LibraryTablesRegistrar.getInstance().getLibraryTable(project), getProjectBaseDir(project), "Project Libraries");
    if (projectLibs != null) {
      add(projectLibs);
    }

    final Generator globalLibs = factory.create(LibraryTablesRegistrar.getInstance().getLibraryTable(), null, "Global Libraries");
    if (globalLibs != null) {
      add(globalLibs);
    }

    LibraryTable appServerLibraryTable = ((ApplicationServersManagerImpl)ApplicationServersManager.getInstance()).getLibraryTable();
    if (appServerLibraryTable.getLibraries().length != 0) {
      final Generator appServerLibs = factory.create(appServerLibraryTable, null, "Application Server Libraries");
      if (appServerLibs != null){
        add(appServerLibs);
      }
    }
  }

  private void createJdkGenerators(final Project project) {
    final ProjectJdk[] jdks = getUsedJdks(project);

    if (jdks.length > 0) {
      add(new Comment("JDK definitions"), 1);

      for (int idx = 0; idx < jdks.length; idx++) {
        final ProjectJdk jdk = jdks[idx];
        if (jdk.getHomeDirectory() == null) {
          continue;
        }
        final File homeDir = VfsUtil.virtualToIoFile(jdk.getHomeDirectory());
        final String jdkName = jdk.getName();
        final String jdkHomeProperty = getJdkHomeProperty(jdkName);
        final FileSet fileSet = new FileSet(propertyRef(jdkHomeProperty));
        final String[] urls = jdk.getRootProvider().getUrls(OrderRootType.COMPILATION_CLASSES);
        for (int i = 0; i < urls.length; i++) {
          final String path = GenerationUtils.trimJarSeparator(VirtualFileManager.extractPath(urls[i]));
          final File pathElement = new File(path);
          final String relativePath = FileUtil.getRelativePath(homeDir, pathElement);
          if (relativePath != null) {
            fileSet.add(new Include(relativePath.replace(File.separatorChar, '/')));
          }
        }
        //add(new Property(jdkHomeProperty, homeDir.getPath().replace(File.separatorChar, '/')), 1);
        final Path jdkPath = new Path(getJdkPathId(jdkName));
        jdkPath.add(fileSet);
        add(jdkPath);
      }
    }

    final ProjectJdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    add(new Property(PROPERTY_PROJECT_JDK_HOME, projectJdk != null? propertyRef(getJdkHomeProperty(projectJdk.getName())) : ""), 1);
    add(new Property(PROPERTY_PROJECT_JDK_CLASSPATH, projectJdk != null? getJdkPathId(projectJdk.getName()) : ""));
  }

  public static ProjectJdk[] getUsedJdks(Project project) {
    final Set<ProjectJdk> jdks = new HashSet<ProjectJdk>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (int idx = 0; idx < modules.length; idx++) {
      Module module = modules[idx];
      ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk != null) {
        jdks.add(jdk);
      }
    }
    return jdks.toArray(new ProjectJdk[jdks.size()]);
  }

  public static String getPropertyFileName(Project project) {
    return getProjectBuildFileName(project) + ".properties";
  }

  public static String getJdkPathId(final String jdkName) {
    return "jdk.classpath." + convertName(jdkName);
  }

  public static String getModuleChunkJdkClasspathProperty(final String moduleChunkName) {
    return "module.jdk.classpath." + convertName(moduleChunkName);
  }

  public static String getModuleChunkJdkHomeProperty(final String moduleChunkName) {
    return "module.jdk.home." + convertName(moduleChunkName);
  }

  public static String getModuleChunkCompilerArgsProperty(final String moduleName) {
    return "compiler.args." + convertName(moduleName);
  }

  public static String getLibraryPathId(final String libraryName) {
    return "library." + convertName(libraryName) + ".classpath";
  }

  public static String getJdkHomeProperty(final String jdkName) {
    return "jdk.home." + convertName(jdkName);
  }

  public static String getCompileTargetName(String moduleName) {
    return "compile.module." + convertName(moduleName);
  }

  public static String getOutputPathProperty(String moduleName) {
    return convertName(moduleName) + ".output.dir";
  }

  public static String getOutputPathForTestsProperty(String moduleName) {
    return convertName(moduleName) + ".testoutput.dir";
  }

  public static String getClasspathProperty(String moduleName) {
    return convertName(moduleName) + ".module.classpath";
  }

  public static String getBootClasspathProperty(String moduleName) {
    return convertName(moduleName) + ".module.bootclasspath";
  }

  public static String getSourcepathProperty(String moduleName) {
    return convertName(moduleName) + ".module.sourcepath";
  }

  public static String getTestSourcepathProperty(String moduleName) {
    return convertName(moduleName) + ".module.test.sourcepath";
  }

  public static String getExcludedFromModuleProperty(String moduleName) {
    return "excluded.from.module." + convertName(moduleName);
  }

  public static String getExcludedFromCompilationProperty(String moduleName) {
    return "excluded.from.compilation." + convertName(moduleName);
  }

  public static String getProjectBuildFileName(Project project) {
    return convertName(project.getName());
  }

  public static String getModuleChunkBuildFileName(final ModuleChunk chunk) {
    return "module_" + convertName(chunk.getName());
  }

  public static String getModuleCleanTargetName(String moduleName) {
    return "clean.module." + convertName(moduleName);
  }

  public static String getModuleChunkBasedirProperty(ModuleChunk chunk) {
    return "module." + convertName(chunk.getName()) + ".basedir";
  }

  /**
   * left for compatibility
   */ 
  public static String getModuleBasedirProperty(Module module) {
    return "module." + convertName(module.getName()) + ".basedir";
  }

  public static String getProjectBaseDirProperty() {
    return "basedir";
  }

  public static File getModuleChunkBaseDir(ModuleChunk chunk) {
    return chunk.getBaseDir();
  }

  public static File getProjectBaseDir(final Project project) {
    return new File(project.getProjectFilePath()).getParentFile();
  }

  private static String convertName(final String name) {
    return JDOMUtil.escapeText(name.replaceAll("\"", "").replaceAll("\\s+", "_").toLowerCase());
  }

  public static String getPathMacroProperty(String pathMacro) {
    return "path.variable." + convertName(pathMacro);
  }

  // J2EE
  public static String getJ2EEExplodedPathProperty(String moduleName) {
    return convertName(moduleName) + ".dir.exploded";
  }

  public static String getJ2EEExplodedPathProperty() {
    return "j2ee.dir.exploded";
  }

  public static String getJ2EEJarPathProperty() {
    return "j2ee.path.jar";
  }

  public static String getJ2EEJarPathProperty(String moduleName) {
    return convertName(moduleName) + ".path.jar";
  }

  public static String getJ2EEBuildTargetName(String moduleName) {
    return "j2ee.build."+convertName(moduleName);
  }

  public static String getJ2EEExplodedBuildTargetName(String moduleName) {
    return "j2ee.build.exploded."+convertName(moduleName);
  }

  public static String getJ2EEJarBuildTargetName(String moduleName) {
    return "j2ee.build.jar."+convertName(moduleName);
  }

  public static String getTempDirForModuleProperty(String moduleName) {
    return "tmp.dir."+convertName(moduleName);
  }

  public static String propertyRef(String propertyName) {
    return "${" + propertyName + "}";
  }
}
