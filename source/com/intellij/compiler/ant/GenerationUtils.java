package com.intellij.compiler.ant;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class GenerationUtils {

  public static String toRelativePath(final VirtualFile file,
                                      File baseDir,
                                      final String baseDirPropertyName,
                                      GenerationOptions genOptions,
                                      boolean useAbsolutePathsForOuterPaths) {
    return toRelativePath(PathUtil.getLocalPath(file).replace(File.separatorChar, '/'), baseDir, baseDirPropertyName, genOptions, useAbsolutePathsForOuterPaths);
  }

  public static String toRelativePath(final String path,
                                      File baseDir,
                                      final String baseDirPropertyName,
                                      GenerationOptions genOptions,
                                      boolean useAbsolutePathsForOuterPaths) {
    if (baseDir != null) {
      final String relativepath = FileUtil.getRelativePath(baseDir, new File(path));
      if (relativepath != null) {
        if (!useAbsolutePathsForOuterPaths || relativepath.indexOf("..") < 0) {
          final String _relativePath = relativepath.replace(File.separatorChar, '/');
          final String root = BuildProperties.propertyRef(baseDirPropertyName);
          return ".".equals(_relativePath)? root : root + "/" + _relativePath;
        }
      }
    }
    return genOptions.subsitutePathWithMacros(path);
  }

  public static String trimJarSeparator(final String path) {
    return path.endsWith(JarFileSystem.JAR_SEPARATOR)? path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()) : path;
  }

}
