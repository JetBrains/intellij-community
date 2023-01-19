// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base for cases that need marked PSI elements.
 */
public abstract class LightMarkedTestCase extends PyTestCase {
  protected PsiFile myFile;

  /**
   * Marker "as expected", any alphanumeric sting in angle brackets.
   */
  public static final @NonNls String MARKER = "<[a-zA-Z0-9_]+>";

  /**
   * Uses MARKER as regexp.
   * @see #configureByFileText(String, String, String)
   * @param filePath file to load and parse
   * @return a mapping of markers to PSI elements
   */
  protected Map<String, PsiElement> configureByFile(@TestDataFile @NonNls String filePath) {
    return configureByFile(filePath, MARKER);
  }

  /**
   * Like <a href="#configureByFileText(String, String, String)">configureByFileText</a>, but with a file to be read.
   * @param filePath file to read and parse
   * @param markerRegexp regexp for markers
   * @return a mapping of markers to PSI elements
   */
  protected Map<String, PsiElement> configureByFile(@TestDataFile @NonNls String filePath, @NonNls String markerRegexp) {
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = getVirtualFileByName(fullPath);
    assertNotNull("file " + fullPath + " not found", vFile);

    final String text;
    try {
      text = VfsUtilCore.loadText(vFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String fileText = StringUtil.convertLineSeparators(text, "\n");

    final String fileName = vFile.getName();

    return configureByFileText(fileText, fileName, markerRegexp);
  }

  /**
   * Typically a text is marked with patterns: "foo &lt;ref1>bar() + &lt;ref2>baz", etc, and the result is
   * a map where strings "&lt;ref1>" and "&lt;ref2>" are mapped to PSI elements for "bar" and "baz".
   * @param fileText text to parse
   * @param fileName name to give to the PSI file
   * @param markerRegexp regexp to detect markers in the text
   * @return mapping of markers to the PSI elements
   */
  protected Map<String, PsiElement> configureByFileText(String fileText, final String fileName, @NonNls String markerRegexp) {
    // build a map of marks to positions, and the text with marks stripped
    Pattern pat = Pattern.compile(markerRegexp);
    Matcher mat = pat.matcher(fileText);
    int rest_index = 0; // from here on fileText is not yet looked at
    Map<String, Integer> offsets = new HashMap<>();
    final StringBuilder text = new StringBuilder();
    while (mat.find(rest_index)) {
      String mark = mat.group();
      CharSequence prev_part = fileText.subSequence(rest_index, mat.start());
      text.append(prev_part);
      offsets.put(mark, text.length());
      rest_index = mat.end();
    }
    if (rest_index < fileText.length()) text.append(fileText.substring(rest_index));

    // create a file and map marks to PSI elements
    Map<String, PsiElement> result = new HashMap<>();
    myFile = myFixture.addFileToProject(fileName, text.toString());
    myFixture.configureFromExistingVirtualFile(myFile.getVirtualFile());
    for (Map.Entry<String, Integer> entry : offsets.entrySet()) {
      result.put(entry.getKey(), myFile.findElementAt(entry.getValue()));
    }
    return result;
  }

  protected Map<String, PsiElement> loadTest() {
    String fname = getTestName(false) + ".py";
    return configureByFile(fname);
  }
}
