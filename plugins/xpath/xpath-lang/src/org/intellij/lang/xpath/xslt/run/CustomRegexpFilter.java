/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.InvalidExpressionException;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.io.URLUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copy of com.intellij.execution.filters.RegexpFilter
 *
 * Sascha Weinreuter - 2005-11-07 - Modfied to be able to use a customized filename/url pattern
 *
 * @author Yura Cangea
 * @version 1.0
 */
@SuppressWarnings({"ALL"})
public class CustomRegexpFilter implements Filter {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String FILE_PATH_MACROS = "$FILE_PATH$";
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String LINE_MACROS = "$LINE$";
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String COLUMN_MACROS = "$COLUMN$";

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static final String DEFAULT_REGEXP = "((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)";
  private static final String NUMBER_REGEXP = "([0-9]+)";

  private int myFileRegister;
  private int myLineRegister;
  private int myColumnRegister;

  private Pattern myPattern;
  private Project myProject;
  private final VirtualFile myBase;
  private final String myFilePathRegexp;

  public CustomRegexpFilter(Project project, String expression, VirtualFile base) {
    this(project, expression, base, DEFAULT_REGEXP);
  }

  public CustomRegexpFilter(Project project, String expression, VirtualFile base, String filePathExpr) {
    myFilePathRegexp = filePathExpr;

    myProject = project;
    myBase = base;
    validate(expression);

    if (expression == null || "".equals(expression.trim())) {
        throw new InvalidExpressionException("expression == null or empty");
    }

    int filePathIndex = expression.indexOf(FILE_PATH_MACROS);
    int lineIndex = expression.indexOf(LINE_MACROS);
    int columnIndex = expression.indexOf(COLUMN_MACROS);

    if (filePathIndex == -1) {
        throw new InvalidExpressionException("Expression must contain " + FILE_PATH_MACROS + " macros.");
    }

    final TreeMap<Integer, String> map = new TreeMap<Integer, String>();

    map.put(new Integer(filePathIndex), "file");

    expression = StringUtil.replace(expression, FILE_PATH_MACROS, myFilePathRegexp);

    if (lineIndex != -1) {
        expression = StringUtil.replace(expression, LINE_MACROS, NUMBER_REGEXP);
        map.put(new Integer(lineIndex), "line");
    }

    if (columnIndex != -1) {
        expression = StringUtil.replace(expression, COLUMN_MACROS, NUMBER_REGEXP);
        map.put(new Integer(columnIndex), "column");
    }

    // The block below determines the registers based on the sorted map.
    int count = 0;
    final Iterator<Integer> itr = map.keySet().iterator();
    while (itr.hasNext()) {
        count++;
        final String s = map.get(itr.next());

        if ("file".equals(s)) {
            filePathIndex = count;
        } else if ("line".equals(s)) {
            lineIndex = count;
        } else if ("column".equals(s)) {
            columnIndex = count;
        }
    }

    myFileRegister = filePathIndex;
    myLineRegister = lineIndex;
    myColumnRegister = columnIndex;
    myPattern = Pattern.compile(expression, Pattern.MULTILINE);
  }

  public void validate(String expression) {
    if (expression == null || "".equals(expression.trim())) {
      throw new InvalidExpressionException("expression == null or empty");
    }

    expression = substituteMacrosesWithRegexps(expression);

    Pattern.compile(expression, Pattern.MULTILINE);
  }

  private String substituteMacrosesWithRegexps(String expression) {
    int filePathIndex = expression.indexOf(FILE_PATH_MACROS);
    int lineIndex = expression.indexOf(LINE_MACROS);
    int columnIndex = expression.indexOf(COLUMN_MACROS);

    if (filePathIndex == -1) {
      throw new InvalidExpressionException("Expression must contain " + FILE_PATH_MACROS + " macros.");
    }

    expression = StringUtil.replace(expression, FILE_PATH_MACROS, myFilePathRegexp);

    if (lineIndex != -1) {
      expression = StringUtil.replace(expression, LINE_MACROS, NUMBER_REGEXP);
    }

    if (columnIndex != -1) {
      expression = StringUtil.replace(expression, COLUMN_MACROS, NUMBER_REGEXP);
    }
    return expression;
  }

  @SuppressWarnings({"ConstantConditions"})
  public Result applyFilter(final String line, final int entireLength) {

    final Matcher matcher = myPattern.matcher(line);
    if (matcher.find()) {
      return createResult(matcher, entireLength - line.length());
    }

    return null;
  }

  private Result createResult(final Matcher matcher, final int entireLen) {
    final String filePath = matcher.group(myFileRegister);

    String lineNumber = "0";
    String columnNumber = "0";

    if (myLineRegister != -1) {
      lineNumber = matcher.group(myLineRegister);
    }

    if (myColumnRegister != -1) {
      columnNumber = matcher.group(myColumnRegister);
    }

    int line = 0;
    int column = 0;
    try {
      line = Integer.parseInt(lineNumber);
      column = Integer.parseInt(columnNumber);
    } catch (NumberFormatException e) {
      // Do nothing, so that line and column will remain at their initial
      // zero values.
    }

    if (line > 0) line -= 1;
    if (column > 0) column -= 1;
    // Calculate the offsets relative to the entire text.
    final int highlightStartOffset;
    final int highlightEndOffset;
    if ((filePath == null || filePath.length() == 0) && myLineRegister != -1) {
      highlightStartOffset = entireLen + matcher.start(myLineRegister);
      highlightEndOffset = highlightStartOffset + lineNumber.length();
    } else {
      highlightStartOffset = entireLen + matcher.start(myFileRegister);
      highlightEndOffset = highlightStartOffset + filePath.length();
    }
    final HyperlinkInfo info = createOpenFileHyperlink(filePath, line, column);
    // don't return a result if the filename cannot be resolved to a file
    return info != null ? new Result(highlightStartOffset, highlightEndOffset, info) : null;
  }

  protected HyperlinkInfo createOpenFileHyperlink(String fileName, final int line, int column) {
    if ((fileName == null || fileName.length() == 0)) {
        if (myBase != null) {
            fileName = myBase.getPresentableUrl();
        } else {
            return null;
        }
    }
    fileName = fileName.replace(File.separatorChar, '/');

    VirtualFile file;
    // try to interpret the filename as URL
    if (URLUtil.containsScheme(fileName)) {
      try {
        file = VfsUtil.findFileByURL(new URL(fileName));
      } catch (MalformedURLException e) {
        file = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(fileName));
      }
    } else {
      file = VfsUtil.findRelativeFile(fileName, myBase);
    }
    if (file == null) {
      //noinspection ConstantConditions
      return null;
    }

    final FileType fileType = file.getFileType();
    if (fileType != null && column > 0) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);

      final int start = document.getLineStartOffset(line);
      final int max = document.getLineEndOffset(line);
      final int tabSize = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getTabSize(fileType);
      column = EditorUtil.calcColumnNumber(null, document.getCharsSequence(), start, Math.min(start + column, max), tabSize);
    }
    return new OpenFileHyperlinkInfo(myProject, file, line, column);
  }

  public static String[] getMacrosName() {
    return new String[] {FILE_PATH_MACROS, LINE_MACROS, COLUMN_MACROS};
  }
}
