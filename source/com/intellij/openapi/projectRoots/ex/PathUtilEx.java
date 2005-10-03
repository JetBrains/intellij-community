package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.CollectUtil;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FilteringIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 14, 2004
 */
public class PathUtilEx {
  @NonNls private static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  private static final Convertor<Module, ProjectJdk> MODULE_JDK = new Convertor<Module, ProjectJdk>() {
    public ProjectJdk convert(Module module) {
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

  public static String getIdeaRtJarPath() {
    final Class aClass = JavacRunner.class;
    return PathUtil.getJarPathForClass(aClass);
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
    ArrayList<ProjectJdk> jdks = CollectUtil.SKIP_NULLS.toList(FilteringIterator.skipNulls(modules.iterator()), MODULE_JDK);
    if (jdks.size() == 0) {
      return null;
    }
    Collections.sort(jdks, ComparatorUtil.compareBy(JDK_VERSION, String.CASE_INSENSITIVE_ORDER));
    return jdks.get(jdks.size() - 1);
  }
}
