package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class CompileModuleChunkTarget extends CompositeGenerator {
  private final Target myMainTarget;
  private final Target myProductionTarget;
  private final Target myTestsTarget;

  public CompileModuleChunkTarget(final Project project, ModuleChunk moduleChunk, VirtualFile[] sourceRoots, VirtualFile[] testSourceRoots, File baseDir, GenerationOptions genOptions) {
    final String moduleChunkName = moduleChunk.getName();
    final Tag compilerArgs = new Tag("compilerarg", new Pair[]{new Pair<String, String>("line", BuildProperties.propertyRef(BuildProperties.getModuleChunkCompilerArgsProperty(moduleChunkName)))});
    final Tag classpathTag = new Tag("classpath", new Pair[]{new Pair<String, String>("refid", BuildProperties.getClasspathProperty(moduleChunkName))});
    final Tag bootclasspathTag = new Tag("bootclasspath", new Pair[]{new Pair<String, String>("refid", BuildProperties.getBootClasspathProperty(moduleChunkName))});
    final PatternSetRef compilerExcludes = CompilerExcludes.isAvailable(project)? new PatternSetRef(BuildProperties.getExcludedFromCompilationProperty(moduleChunkName)) : null;

    final String mainTargetName = BuildProperties.getCompileTargetName(moduleChunkName);
    final String productionTargetName = mainTargetName + ".production";
    final String testsTargetName = mainTargetName + ".tests";

    myMainTarget = new Target(mainTargetName, productionTargetName + "," + testsTargetName, "compile module(s) " + moduleChunkName, null);
    myProductionTarget = new Target(productionTargetName, getChunkDependenciesString(moduleChunk), "compile module(s) " + moduleChunkName + " production classes", null);
    myTestsTarget = new Target(testsTargetName, productionTargetName, "compile module(s) " + moduleChunkName + " test classes", BuildProperties.PROPERTY_SKIP_TESTS);

    if (sourceRoots.length > 0) {
      final String outputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName));
      myProductionTarget.add(new Mkdir(outputPathRef));

      final Javac javac = new Javac(genOptions.enableFormCompiler? "javac2" : "javac", moduleChunkName, outputPathRef);
      javac.add(compilerArgs);
      javac.add(bootclasspathTag);
      javac.add(classpathTag);
      javac.add(new Tag("src", new Pair[]{new Pair<String, String>("refid", BuildProperties.getSourcepathProperty(moduleChunkName))}));
      if (compilerExcludes != null) {
        javac.add(compilerExcludes);
      }

      myProductionTarget.add(javac);
      myProductionTarget.add(createCopyTask(project, moduleChunk, sourceRoots, outputPathRef, baseDir, genOptions));
    }

    if (testSourceRoots.length > 0) {
      final String testOutputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathForTestsProperty(moduleChunkName));
      myTestsTarget.add(new Mkdir(testOutputPathRef));

      final Javac javac = new Javac(genOptions.enableFormCompiler? "javac2" : "javac", moduleChunkName, testOutputPathRef);
      javac.add(compilerArgs);
      javac.add(classpathTag);
      javac.add(new Tag("classpath", new Pair[]{new Pair<String, String>("location", BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName)))}));
      javac.add(new Tag("src", new Pair[]{new Pair<String, String>("refid", BuildProperties.getTestSourcepathProperty(moduleChunkName))}));
      if (compilerExcludes != null) {
        javac.add(compilerExcludes);
      }

      myTestsTarget.add(javac);
      myTestsTarget.add(createCopyTask(project, moduleChunk, testSourceRoots, testOutputPathRef, baseDir, genOptions));
    }

    add(myMainTarget);
    add(myProductionTarget, 1);
    add(myTestsTarget, 1);
  }

  private String getChunkDependenciesString(ModuleChunk moduleChunk) {
    final StringBuffer moduleDependencies = new StringBuffer();
    final ModuleChunk[] dependencies = moduleChunk.getDependentChunks();
    for (int idx = 0; idx < dependencies.length; idx++) {
      final ModuleChunk dependency = dependencies[idx];
      if (idx > 0) {
        moduleDependencies.append(",");
      }
      moduleDependencies.append(BuildProperties.getCompileTargetName(dependency.getName()));
    }
    return moduleDependencies.toString();
  }

  private static Generator createCopyTask(final Project project, ModuleChunk chunk, VirtualFile[] sourceRoots, String toDir, File baseDir, final GenerationOptions genOptions) {
    final Tag filesSelector = new Tag("type", new Pair[] {new Pair("type", "file")});
    final PatternSetRef excludes = CompilerExcludes.isAvailable(project)? new PatternSetRef(BuildProperties.getExcludedFromCompilationProperty(chunk.getName())) : null;
    //final String resourcePatternsPropertyRef = propertyRef(BuildProperties.PROPERTY_COMPILER_RESOURCE_PATTERNS);
    final PatternSetRef resourcePatternsPatternSet = new PatternSetRef(BuildProperties.PROPERTY_COMPILER_RESOURCE_PATTERNS);
    final Copy copy = new Copy(toDir);
    for (int idx = 0; idx < sourceRoots.length; idx++) {
      final VirtualFile root = sourceRoots[idx];
      final FileSet fileSet = new FileSet(GenerationUtils.toRelativePath(root, baseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !chunk.isSavePathsRelative()));
      fileSet.add(resourcePatternsPatternSet);
      fileSet.add(filesSelector);
      if (excludes != null) {
        fileSet.add(excludes);
      }
      copy.add(fileSet);
    }
    return copy;
  }
}
