/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.structuralsearch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;

import java.io.IOException;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 14, 2006
 * Time: 9:45:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestUtils {
  static String loadFile(String fileName) throws IOException {
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return loadFile(fileName, fileType.getDefaultExtension().toLowerCase());
  }

  static String loadFile(String fileName, String dir) throws IOException {
    return StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(new File(getBasePath()+"/" + dir + "/" + fileName))));
  }

  static String getBasePath() {
    return PathManager.getHomePath() + File.separatorChar + "plugins/structuralsearch" + File.separatorChar + "testData";
  }
}
