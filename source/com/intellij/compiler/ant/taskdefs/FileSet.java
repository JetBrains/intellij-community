package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class FileSet extends Tag{

  public FileSet(final String dir) {
    super("fileset", new Pair[] {pair("dir", dir)});
  }

}
