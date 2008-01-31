package com.intellij.openapi.projectRoots.ex;

import org.jetbrains.annotations.NonNls;
import com.intellij.util.PathsList;
import com.intellij.util.PathUtil;
import com.intellij.rt.junit4.JUnit4Util;
import com.intellij.rt.compiler.JavacRunner;

public class JavaSdkUtil {
  @NonNls public static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }

  public static void addJunit4RtJar(PathsList pathsList) {
    final String path = getIdeaJunit4RtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(path);
    }
    else {
      pathsList.addTail(path);
    }
  }

  public static String getIdeaJunit4RtJarPath() {
    return PathUtil.getJarPathForClass(JUnit4Util.class);
  }

  public static String getJunit4JarPath() {
    return PathUtil.getJarPathForClass(org.junit.Test.class);
  }

  public static String getJunit3JarPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(JavacRunner.class);
  }
}
