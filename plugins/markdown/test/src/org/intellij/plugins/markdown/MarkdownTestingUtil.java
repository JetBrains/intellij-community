package org.intellij.plugins.markdown;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;

import java.io.File;

public class MarkdownTestingUtil {
  public static final String TEST_DATA_PATH = findTestDataPath();

  private MarkdownTestingUtil() {
  }

  private static String findTestDataPath() {
    if (new File(PathManager.getHomePath() + "/community/plugins").isDirectory()) {
      return FileUtil.toSystemIndependentName(PathManager.getHomePath() + "/community/plugins/markdown/test/data");
    }

    final String parentPath = PathUtil.getParentPath(PathManager.getHomePath());

    if (new File(parentPath + "/intellij-plugins").isDirectory()) {
      return FileUtil.toSystemIndependentName(parentPath + "/intellij-plugins/markdown/test/data");
    }

    if (new File(parentPath + "/community/plugins").isDirectory()) {
      return FileUtil.toSystemIndependentName(parentPath + "/community/plugins/markdown/test/data");
    }

    return "";
  }
}
