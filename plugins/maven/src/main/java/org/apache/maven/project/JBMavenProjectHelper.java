package org.apache.maven.project;

import java.util.List;

public class JBMavenProjectHelper {
  public static void setSourceRoots(MavenProject project,
                                    List<String> compileSourceRoots,
                                    List<String> testCompileSourceRoots,
                                    List<String> scriptSourceRoots) {
    project.setCompileSourceRoots(compileSourceRoots);
    project.setTestCompileSourceRoots(testCompileSourceRoots);
    project.setScriptSourceRoots(scriptSourceRoots);
  }
}
