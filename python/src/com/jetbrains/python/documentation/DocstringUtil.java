package com.jetbrains.python.documentation;

import com.intellij.openapi.util.text.StringUtil;

/**
 * User: catherine
 */
public class DocstringUtil {

  public static String unifyDocstring(String docstring) {
    docstring = StringUtil.replace(docstring, ":py:class:", "");
    docstring = StringUtil.replace(docstring, ":class:", "");
    docstring = docstring.replaceAll("`([^`]+)`", "$1");
    return docstring;
  }

}
