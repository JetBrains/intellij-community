package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Target;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public class CleanProject extends Generator {
  private Target myTarget;

  public CleanProject(GenerationOptions genOptions) {
    StringBuffer dependencies = new StringBuffer();
    final ModuleChunk[] chunks = genOptions.getModuleChunks();
    for (int idx = 0; idx < chunks.length; idx++) {
      if (idx > 0) {
        dependencies.append(", ");
      }
      dependencies.append(BuildProperties.getModuleCleanTargetName(chunks[idx].getName()));
    }
    myTarget = new Target(BuildProperties.TARGET_CLEAN, dependencies.toString(), "cleanup all", null);
  }

  public void generate(DataOutput out) throws IOException {
    myTarget.generate(out);
  }
}
