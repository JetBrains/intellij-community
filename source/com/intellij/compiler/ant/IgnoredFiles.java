package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Exclude;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;

import java.io.DataOutput;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class IgnoredFiles extends Generator{
  private final PatternSet myPatternSet;

  public IgnoredFiles() {
    myPatternSet = new com.intellij.compiler.ant.taskdefs.PatternSet(BuildProperties.PROPERTY_IGNORED_FILES);
    final StringTokenizer tokenizer = new StringTokenizer(((FileTypeManagerEx)FileTypeManager.getInstance()).getIgnoredFilesList(), ";", false);
    while(tokenizer.hasMoreTokens()) {
      final String filemask = tokenizer.nextToken();
      myPatternSet.add(new Exclude("**/" + filemask + "/**"));
    }
  }



  public void generate(DataOutput out) throws IOException {
    myPatternSet.generate(out);
  }
}
