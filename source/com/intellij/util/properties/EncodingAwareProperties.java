package com.intellij.util.properties;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.StringTokenizer;

import java.io.File;
import java.io.IOException;

/**
 * @author MYakovlev
 * Date: Oct 29, 2002
 * Time: 8:47:43 PM
 */
public class EncodingAwareProperties extends java.util.Properties{
  public void load(File file, String encoding) throws IOException{
    String propText = new String(FileUtil.loadFileText(file, encoding));
    propText = StringUtil.convertLineSeparators(propText);
    StringTokenizer stringTokenizer = new StringTokenizer(propText, "\n");
    while (stringTokenizer.hasMoreElements()){
      String line = (String)stringTokenizer.nextElement();
      int i = line.indexOf('=');
      String propName = i == -1 ? line : line.substring(0,i);
      String propValue = i == -1 ? "" : line.substring(i+1);
      setProperty(propName, propValue);
    }
  }
}
