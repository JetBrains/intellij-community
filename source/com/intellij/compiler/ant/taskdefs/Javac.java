package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public class Javac extends Tag{

  public Javac(final String outputDir, final String moduleName) {
    this("javac", moduleName, outputDir);
  }
  
  public Javac(final String taskName, String moduleName, final String outputDir) {
    super(taskName, new Pair[]{
      pair("destdir", outputDir),
      pair("debug", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_DEBUG_INFO)),
      pair("nowarn", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_NO_WARNINGS)),
      pair("memoryMaximumSize", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_MAX_MEMORY)),
      pair("fork", "true"),
      pair("executable", getExecutable(moduleName))
    });
  }

  private static String getExecutable(String moduleName) {
    if (moduleName == null) {
      return null;
    }
    return BuildProperties.propertyRef(BuildProperties.getModuleChunkJdkHomeProperty(moduleName)) + "/bin/javac";
  }
}
