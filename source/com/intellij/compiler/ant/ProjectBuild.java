package com.intellij.compiler.ant;

import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.ant.taskdefs.AntProject;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;

import javax.naming.OperationNotSupportedException;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public abstract class ProjectBuild extends Generator {
  protected final Project myProject;
  private final AntProject myAntProject;

  public ProjectBuild(Project project, GenerationOptions genOptions) {
    myProject = project;
    myAntProject = new AntProject(BuildProperties.getProjectBuildFileName(myProject), BuildProperties.DEFAULT_TARGET);

    myAntProject.add(new BuildProperties(myProject, genOptions), 1);

    // the sequence in which modules are imported is important cause output path properties for dependent modules should be defined first

    final StringBuffer alltargetNames = new StringBuffer();
    alltargetNames.append(BuildProperties.TARGET_INIT);
    alltargetNames.append(", ");
    alltargetNames.append(BuildProperties.TARGET_CLEAN);
    final ModuleChunk[] chunks = genOptions.getModuleChunks();

    if (chunks.length > 0) {
      myAntProject.add(new Comment("Modules"), 1);

      for (int idx = 0; idx < chunks.length; idx++) {
        final ModuleChunk chunk = chunks[idx];
        myAntProject.add(createModuleBuildGenerator(chunk, genOptions), 1);
        if (alltargetNames.length() > 0) {
          alltargetNames.append(", ");
        }
        final String chunkName = chunk.getName();

        if (chunk.isJ2EE()) {
          alltargetNames.append(BuildProperties.getJ2EEBuildTargetName(chunkName));
        }
        else {
          alltargetNames.append(BuildProperties.getCompileTargetName(chunkName));
        }
      }
    }

    final Target initTarget = new Target(BuildProperties.TARGET_INIT, null, "Build initialization", null);
    initTarget.add(new Comment("Perform any build initialization in this target"));
    myAntProject.add(initTarget, 1);
    myAntProject.add(new CleanProject(genOptions), 1);
    myAntProject.add(new Target(BuildProperties.TARGET_ALL, alltargetNames.toString(), "build all", null), 1);
  }

  public void generate(DataOutput out) throws IOException {
    out.writeBytes("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
    crlf(out);
    myAntProject.generate(out);
  }

  protected abstract Generator createModuleBuildGenerator(final ModuleChunk chunk, GenerationOptions genOptions);

}
