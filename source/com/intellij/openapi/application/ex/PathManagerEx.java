/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 19, 2002
 * Time: 8:21:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.PathManager;

import java.io.File;

import org.jetbrains.annotations.NonNls;

public class PathManagerEx {
  @NonNls private static final String TESTDATA_DIRECTORY = "testData";

  public static String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + TESTDATA_DIRECTORY;
  }

  public static String getPluginTempPath () {
    String systemPath = PathManager.getSystemPath();

    String filePath = systemPath + File.separator + PathManager.PLUGINS_DIRECTORY;
    return filePath;
  }

}
