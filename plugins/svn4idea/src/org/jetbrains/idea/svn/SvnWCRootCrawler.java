package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 28.06.2005
 * Time: 19:14:57
 * To change this template use File | Settings | File Templates.
 */
public interface SvnWCRootCrawler {
  public Collection handleWorkingCopyRoot(File root, ProgressIndicator progress);
}
