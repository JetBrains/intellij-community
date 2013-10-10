/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.SystemProperties;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Writer;
import java.util.Properties;

/**
 * @author Denis Zhdanov
 * @since 10/5/11 2:35 PM
 */
public class ResourceBundleUtil {

  private static final String NATIVE_2_ASCII_CONVERSION_PATTERN = SystemProperties.getBooleanProperty("idea.native2ascii.lowercase", false)
                                                                  ? "\\u%04x" : "\\u%04X";

  private static final TIntHashSet SYMBOLS_TO_ESCAPE = new TIntHashSet(new int[]{'#', '!', '=', ':'});
  private static final TIntHashSet UNICODE_SYMBOLS   = new TIntHashSet(new int[]{
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'A', 'a', 'A', 'b', 'B', 'c', 'C', 'd', 'D', 'e', 'E', 'f', 'F'
  });
  private static final char        ESCAPE_SYMBOL     = '\\';

  private ResourceBundleUtil() {
  }

  /**
   * Tries to derive {@link com.intellij.lang.properties.ResourceBundle resource bundle} related to the given context.
   *
   * @param dataContext   target context
   * @return              {@link com.intellij.lang.properties.ResourceBundle resource bundle} related to the given context if any;
   *                      <code>null</code> otherwise
   */
  @Nullable
  public static ResourceBundle getResourceBundleFromDataContext(@NotNull DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof IProperty) return null; //rename property
    final ResourceBundle[] bundles = ResourceBundle.ARRAY_DATA_KEY.getData(dataContext);
    if (bundles != null && bundles.length == 1) return bundles[0];
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) {
      return null;
    }
    if (virtualFile instanceof ResourceBundleAsVirtualFile) {
      return ((ResourceBundleAsVirtualFile)virtualFile).getResourceBundle();
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) {
        return ((PropertiesFile)psiFile).getResourceBundle();
      }
    }
    return null;
  }

  /**
   * Tries to derive {@link ResourceBundleEditor resource bundle editor} identified by the given context. 
   *
   * @param dataContext     target data context
   * @return resource bundle editor identified by the given context; <code>null</code> otherwise
   */
  @Nullable
  public static ResourceBundleEditor getEditor(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (virtualFile == null) {
      return null;
    }
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(virtualFile);
    if (editors.length != 1 || (!(editors[0] instanceof ResourceBundleEditor))) {
      return null;
    }

    return (ResourceBundleEditor)editors[0];
  }
  
  /**
   * Allows to map given 'raw' property value text to the 'user-friendly' text to show at the resource bundle editor.
   * <p/>
   * <b>Note:</b> please refer to {@link Properties#store(Writer, String)} contract for the property value escape rules.
   * 
   * @param text  'raw' property value text
   * @return      'user-friendly' text to show at the resource bundle editor
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static String fromPropertyValueToValueEditor(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ESCAPE_SYMBOL && !escaped) {
        char[] unicodeSymbols = parseUnicodeLiteral(text, i + 1);
        if (unicodeSymbols != null) {
          buffer.append(unicodeSymbols);
          i += 5;
        }
        else {
          escaped = true;
        }
        continue;
      }
      if (escaped && c == 'n') {
        buffer.append(ESCAPE_SYMBOL);
      }
      buffer.append(c);
      escaped = false;
    }
    return buffer.toString();
  }

  /**
   * Tries to parse unicode literal contained at the given text at the given offset:
   * <pre>
   *   "my string to process \uABCD - with unicode literal"
   *                          ^
   *                          |
   *                       offset
   * </pre>
   * I.e. this method checks if given text contains u[0123456789AaBbCcDdEeFf]{4} at the given offset; parses target unicode symbol
   * and returns in case of the positive answer.
   * 
   * @param text  text to process
   * @param i     offset which might point to 'u' part of unicode literal contained at the given text
   * @return      16-bit char symbols for the target unicode code point located at the given text at the given offset if any;
   *              <code>null</code> otherwise
   */
  @Nullable
  private static char[] parseUnicodeLiteral(@NotNull String text, int i) {
    if (text.length() < i + 5) {
      return null;
    }
    char c = text.charAt(i);
    if (c != 'u' && c != 'U') {
      return null;
    }
    for (int j = i + 1; j < i + 5; j++) {
      if (!UNICODE_SYMBOLS.contains(text.charAt(j))) {
        return null;
      }
    }
    try {
      int codePoint = Integer.parseInt(text.substring(i + 1, i + 5), 16);
      return Character.toChars(codePoint);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Perform reverse operation to {@link #fromPropertyValueToValueEditor(String)}.
   * 
   * @param text  'user-friendly' text shown to the user at the resource bundle editor
   * @return      'raw' value to store at the *.properties file
   */
  @NotNull
  public static String fromValueEditorToPropertyValue(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      
      if ((i == 0 && (c == ' ' || c == '\t')) // Leading white space
          || c == '\n'  // Multi-line value
          || SYMBOLS_TO_ESCAPE.contains(c))   // Special symbol
      {
        buffer.append(ESCAPE_SYMBOL);
      } 
      else if (c == ESCAPE_SYMBOL) {           // Escaped 'escape' symbol) 
        if (text.length() > i + 1) {
          final char nextChar = text.charAt(i + 1);
          if (nextChar != 'n') {
            buffer.append(ESCAPE_SYMBOL);
          }
        } else {
          buffer.append(ESCAPE_SYMBOL);
        }
      }
      else if (c > 127) { // Non-ascii symbol
        buffer.append(String.format(NATIVE_2_ASCII_CONVERSION_PATTERN, (int)c));
        continue;
      }
      buffer.append(c);
    }
    return buffer.toString();
  }
}
