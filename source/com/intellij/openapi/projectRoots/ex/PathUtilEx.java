package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.rt.junit4.JUnit4Util;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ComparatorUtil;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.skipNulls;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 14, 2004
 */
public class PathUtilEx {
  @NonNls private static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  private static final Function<Module, ProjectJdk> MODULE_JDK = new Function<Module, ProjectJdk>() {
    public ProjectJdk fun(Module module) {
      return ModuleRootManager.getInstance(module).getJdk();
    }
  };
  private static final Convertor<ProjectJdk, String> JDK_VERSION = new Convertor<ProjectJdk, String>() {
    public String convert(ProjectJdk jdk) {
      return jdk.getVersionString();
    }
  };

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }
  public static void addJunit4Jar(PathsList pathsList) {
    final String path = PathUtil.getJarPathForClass(JUnit4Util.class);
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(path);
    }
    else {
      pathsList.addTail(path);
    }
  }

  public static String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(JavacRunner.class);
  }

  public static ProjectJdk getAnyJdk(Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  public static ProjectJdk chooseJdk(Project project, Collection<Module> modules) {
    ProjectJdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    return chooseJdk(modules);
  }

  public static ProjectJdk chooseJdk(Collection<Module> modules) {
    List<ProjectJdk> jdks = skipNulls(map(skipNulls(modules), MODULE_JDK)); 
    if (jdks.isEmpty()) {
      return null;
    }
    Collections.sort(jdks, ComparatorUtil.compareBy(JDK_VERSION, String.CASE_INSENSITIVE_ORDER));
    return jdks.get(jdks.size() - 1);
  }
}
