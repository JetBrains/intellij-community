package com.intellij.compiler.ant;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ant.taskdefs.Exclude;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.compiler.impl.ExcludeEntryDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class CompilerExcludes extends Generator{
  private final PatternSet myPatternSet;

  public CompilerExcludes(Project project, GenerationOptions genOptions) {
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final ExcludeEntryDescription[] excludeEntryDescriptions = compilerConfiguration.getExcludeEntryDescriptions();
    myPatternSet = new PatternSet(BuildProperties.PROPERTY_COMPILER_EXCLUDES);
    for (int idx = 0; idx < excludeEntryDescriptions.length; idx++) {
      final ExcludeEntryDescription entry = excludeEntryDescriptions[idx];
      final String path = genOptions.subsitutePathWithMacros(VirtualFileManager.extractPath(entry.getUrl()));
      if (entry.isFile()) {
        myPatternSet.add(new Exclude(path));
      }
      else {
        if (entry.isIncludeSubdirectories()) {
          myPatternSet.add(new Exclude(path + "/**"));
        }
        else {
          myPatternSet.add(new Exclude(path + "/*"));
        }
      }
    }
  }



  public void generate(DataOutput out) throws IOException {
    myPatternSet.generate(out);
  }

  public static boolean isAvailable(Project project) {
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final ExcludeEntryDescription[] excludeEntryDescriptions = compilerConfiguration.getExcludeEntryDescriptions();
    return excludeEntryDescriptions.length > 0;
  }

}
