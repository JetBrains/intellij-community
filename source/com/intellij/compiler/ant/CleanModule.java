package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Delete;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.module.ModuleType;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public class CleanModule extends Target {
  public CleanModule(ModuleChunk chunk) {
    super(BuildProperties.getModuleCleanTargetName(chunk.getName()), null, "cleanup module", null);
    final String chunkName = chunk.getName();
    if (!chunk.isJ2EEApplication()) {
      add(new Delete(BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(chunkName))));
      add(new Delete(BuildProperties.propertyRef(BuildProperties.getOutputPathForTestsProperty(chunkName))));
    }
  }
}
