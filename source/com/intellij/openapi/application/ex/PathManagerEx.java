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

public class PathManagerEx {
  public static String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + "testData";
  }

  public static String getPluginTempPath () {
    String systemPath = PathManager.getSystemPath();

    String filePath = systemPath + File.separator + "plugins";
    return filePath;
  }

}
