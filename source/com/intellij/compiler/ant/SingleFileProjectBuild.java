package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Dirname;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public class SingleFileProjectBuild extends ProjectBuild {
  public SingleFileProjectBuild(Project project, GenerationOptions genOptions) {
    super(project, genOptions);
  }

  protected Generator createModuleBuildGenerator(ModuleChunk chunk, GenerationOptions genOptions) {
    final CompositeGenerator gen = new CompositeGenerator();
    gen.add(new Comment("Module " + chunk.getName()));
    gen.add(new Dirname(BuildProperties.getModuleChunkBasedirProperty(chunk), BuildProperties.propertyRef("ant.file")), 1);
    gen.add(new ChunkBuild(myProject, chunk, genOptions), 1);
    return gen;
  }

}
