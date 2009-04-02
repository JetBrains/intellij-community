package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base for cases that need marked PSI elements.
 * User: dcheryasov
 * Date: Mar 14, 2009 11:57:52 PM
 */
public abstract class MarkedTestCase extends PsiTestCase {

  /**
   * Marker "as expected", any alphanumeric sting in angle brackets.
   */
  public final @NonNls String MARKER = "<[a-zA-Z0-9_]+>";

  /**
   * Uses MARKER as regexp.
   * @see #configureByFileText(String, String, String)
   * @param filePath file to load and parse
   * @return a mapping of markers to PSI elements
   * @throws Exception
   */
  protected Map<String, PsiElement> configureByFile(@NonNls String filePath) throws Exception {
    return configureByFile(filePath, MARKER);
  }

  /**
   * Like <a href="#configureByFileText(String, String, String)">configureByFileText</a>, but with a file to be read.
   * @param filePath file to read and parse
   * @param markerRegexp regexp for markers
   * @return a mapping of markers to PSI elements
   * @throws Exception
   */
  protected Map<String, PsiElement> configureByFile(@NonNls String filePath, @NonNls String markerRegexp)
  throws Exception
  {
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");

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
   * @throws Exception
   */
  protected Map<String, PsiElement> configureByFileText(String fileText, String fileName, @NonNls String markerRegexp)
  throws Exception
  {
    // build a map of marks to positions, and the text with marks stripped
    Pattern pat = Pattern.compile(markerRegexp);
    Matcher mat = pat.matcher(fileText);
    int rest_index = 0; // from here on fileText is not yet looked at
    Map<String, Integer> offsets = new HashMap<String, Integer>();
    StringBuffer text = new StringBuffer();
    while (mat.find(rest_index)) {
      String mark = mat.group();
      CharSequence prev_part = fileText.subSequence(rest_index, mat.start());
      text.append(prev_part);
      offsets.put(mark, text.length());
      rest_index = mat.end();
    }
    if (rest_index < fileText.length()) text.append(fileText.substring(rest_index));

    // create a file and map marks to PSI elements
    Map<String, PsiElement> result = new HashMap<String, PsiElement>();
    myFile = createFile(myModule, fileName, text.toString());
    for (Map.Entry<String, Integer> entry : offsets.entrySet()) {
      result.put(entry.getKey(), myFile.findElementAt(entry.getValue()));
    }
    return result;
  }

  protected Map<String, PsiElement> loadTest() throws Exception {
    String fname = getTestName(false) + ".py";
    return configureByFile(fname);
  }

  protected abstract String getTestDataPath();
}
