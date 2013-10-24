/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.numpy.documentation;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author avereshchagin
 * @author vlan
 */
public class NumPyDocString {
  private static final Pattern LINE_SEPARATOR = Pattern.compile("\n|\r|\r\n");
  private static final Pattern WHITE_SPACED_LINE = Pattern.compile("^[ \t]+$");
  private static final Pattern ANY_INDENT = Pattern.compile("(^[ \t]*)[^ \t\r\n]");
  private static final Pattern HAS_INDENT = Pattern.compile("(^[ \t]+)[^ \t\r\n]");
  private static final Pattern SIGNATURE = Pattern.compile("^([\\w., ]+=)?\\s*[\\w\\.]+\\(.*\\)$");
  private static final Pattern SECTION_HEADER = Pattern.compile("^[-=]+");
  private static final Pattern PARAMETER_WITH_TYPE = Pattern.compile("^(.+) : (.+)$");
  private static final Pattern PARAMETER_WITHOUT_TYPE = Pattern.compile("^([^ :,]+)$");
  private static final Pattern REDIRECT = Pattern.compile("^Refer to `(.*)` for full documentation.$");
  private static final Pattern NUMPY_UNION_PATTERN = Pattern.compile("^\\{(.*)\\}$");
  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("^(?:\\\"(.*)\\\")|(?:\\'(.*)\\')$");

  private final String mySignature;
  private final List<NumPyDocStringParameter> myParameters = new ArrayList<NumPyDocStringParameter>();
  private final List<NumPyDocStringParameter> myReturns = new ArrayList<NumPyDocStringParameter>();

  private NumPyDocString(@Nullable String signature, @NotNull List<String> lines)
    throws NotNumpyDocStringException {
    mySignature = signature;
    parseSections(lines);
    if (myReturns.size() == 0 && myParameters.size() == 0) {
      throw new NotNumpyDocStringException(signature);
    }
  }

  @Nullable
  public String getSignature() {
    return mySignature;
  }

  @NotNull
  public List<NumPyDocStringParameter> getParameters() {
    return myParameters;
  }

  @NotNull
  public List<NumPyDocStringParameter> getReturns() {
    return myReturns;
  }

  @Nullable
  public NumPyDocStringParameter getNamedParameter(@NotNull String name) {
    for (NumPyDocStringParameter parameter : getParameters()) {
      if (name.equals(parameter.getName())) {
        return parameter;
      }
    }
    return null;
  }

  /**
   * Returns PyFunction object for specified fully qualified name accessible from specified reference.
   *
   * @param redirect  A fully qualified name of function that is redirected to.
   * @param reference An original reference element.
   * @return Resolved function or null if it was not resolved.
   */
  @Nullable
  private static PyFunction resolveRedirectToFunction(@NotNull String redirect, @NotNull PsiElement reference) {
    final QualifiedName qualifiedName = QualifiedName.fromDottedString(redirect);
    final String functionName = qualifiedName.getLastComponent();
    final PyPsiFacade facade = PyPsiFacade.getInstance(reference.getProject());
    final List<PsiElement> items = facade.qualifiedNameResolver(qualifiedName.removeLastComponent()).fromElement(reference).resultsAsList();
    for (PsiElement item : items) {
      if (item instanceof PsiDirectory) {
        item = ((PsiDirectory)item).findFile(PyNames.INIT_DOT_PY);
      }
      if (item instanceof PyFile) {
        final PsiElement element = ((PyFile)item).getElementNamed(functionName);
        if (element instanceof PyFunction) {
          return (PyFunction)element;
        }
      }
    }
    return null;
  }

  @Nullable
  private static NumPyDocString forFunction(@NotNull PyFunction function, @Nullable PsiElement reference, @Nullable String knownSignature) {
    String docString = function.getDocStringValue();
    if (docString == null && "__init__".equals(function.getName())) {
      // Docstring for constructor can be found in the docstring of class
      PyClass cls = function.getContainingClass();
      if (cls != null) {
        docString = cls.getDocStringValue();
      }
    }

    if (docString != null) {
      List<String> lines = splitByLines(docString);
      dedent(lines);

      String signature = null;
      if (!lines.isEmpty() && SIGNATURE.matcher(lines.get(0)).matches()) {
        signature = lines.get(0);
        lines.remove(0);
        dedent(lines);
      }

      String redirect = findRedirect(lines);
      if (redirect != null && reference != null) {
        PyFunction resolvedFunction = resolveRedirectToFunction(redirect, reference);
        if (resolvedFunction != null) {
          return forFunction(resolvedFunction, reference, knownSignature != null ? knownSignature : signature);
        }
      }
      try {
        return new NumPyDocString(knownSignature != null ? knownSignature : signature, lines);
      }
      catch (NotNumpyDocStringException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Returns NumPyDocString object confirming to Numpy-style formatted docstring of specified function.
   *
   * @param function  Function containing docstring for which Numpy wrapper object is to be obtained.
   * @param reference An original reference element to specified function.
   * @return Numpy docstring wrapper object for specified function.
   */
  @Nullable
  public static NumPyDocString forFunction(@NotNull PyFunction function, @Nullable PsiElement reference) {
    return forFunction(function, reference, null);
  }

  @NotNull
  private static List<String> splitByLines(@NotNull String text) {
    List<String> lines = new ArrayList<String>();
    for (String line : LINE_SEPARATOR.split(text)) {
      if (!line.isEmpty() && !WHITE_SPACED_LINE.matcher(line).matches()) {
        lines.add(line);
      }
    }
    return lines;
  }

  private static void dedent(@NotNull List<String> lines) {
    String margin = null;
    for (String line : lines) {
      Matcher matcher = ANY_INDENT.matcher(line);
      if (matcher.find() && matcher.groupCount() != 0) {
        String indent = matcher.group(1);
        if (margin == null || (margin.startsWith(indent) && margin.length() != indent.length())) {
          // update margin
          margin = indent;
        } else if (!indent.startsWith(margin)) {
          // lines have no common margin
          margin = "";
          break;
        }
      }
    }

    if (margin != null && !margin.isEmpty()) {
      for (int i = 0; i < lines.size(); i++) {
        lines.set(i, lines.get(i).substring(margin.length()));
      }
    }
  }

  private static int indexOfMatch(@NotNull List<String> lines, @NotNull Pattern pattern, int start) {
    for (int i = start; i < lines.size(); i++) {
      if (pattern.matcher(lines.get(i)).matches()) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  private static <T> List<T> copyOfRange(@NotNull List<T> src, int start, int end) {
    List<T> dest = new ArrayList<T>();
    if (start < 0) {
      start = 0;
    }
    if (end < 0) {
      end = src.size();
    }
    for (int i = start; i < end; i++) {
      dest.add(src.get(i));
    }
    return dest;
  }

  @Nullable
  private static String findRedirect(@NotNull List<String> lines) {
    for (String line : lines) {
      Matcher matcher = REDIRECT.matcher(line);
      if (matcher.matches() && matcher.groupCount() > 0) {
        return matcher.group(1);
      }
    }
    return null;
  }

  private void parseSections(@NotNull List<String> lines) {
    int current = indexOfMatch(lines, SECTION_HEADER, 1);
    while (current != -1) {
      int next = indexOfMatch(lines, SECTION_HEADER, current + 1);
      String sectionName = lines.get(current - 1);
      if ("Parameters".equalsIgnoreCase(sectionName)) {
        parseParametersSection(copyOfRange(lines, current + 1, next - 1), myParameters);
      } else if ("Returns".equalsIgnoreCase(sectionName)) {
        parseParametersSection(copyOfRange(lines, current + 1, next - 1), myReturns);
      }
      current = next;
    }
  }

  private static void parseParametersSection(@NotNull List<String> lines, List<NumPyDocStringParameter> parameters) {
    DocStringParameterBuilder builder = null;
    for (String line : lines) {
      if (!HAS_INDENT.matcher(line).find()) {
        if (builder != null) {
          parameters.add(builder.build());
        }
        builder = new DocStringParameterBuilder();
        Matcher parameterWithTypeMatcher = PARAMETER_WITH_TYPE.matcher(line);
        if (parameterWithTypeMatcher.matches()) {
          builder.setName(parameterWithTypeMatcher.group(1));
          builder.setType(parameterWithTypeMatcher.group(2));
        } else {
          Matcher parameterWithoutTypeMatcher = PARAMETER_WITHOUT_TYPE.matcher(line);
          if (parameterWithoutTypeMatcher.matches()) {
            builder.setName(parameterWithoutTypeMatcher.group(1));
            builder.setType("object");
          }
        }
      } else {
        if (builder != null) {
          builder.appendDescription(line.trim());
        }
      }
    }
    if (builder != null) {
      parameters.add(builder.build());
    }
  }

  @NotNull
  public static String cleanupOptional(@NotNull String typeString) {
    int index = typeString.indexOf(", optional");
    if (index >= 0) {
      return typeString.substring(0, index);
    }
    return typeString;
  }

  @NotNull
  public static List<String> getNumpyUnionType(@NotNull String typeString) {
    Matcher matcher = NUMPY_UNION_PATTERN.matcher(typeString);
    if (matcher.matches()) {
      typeString = matcher.group(1);
    }
    return Arrays.asList(typeString.split(" *, *"));
  }

  @NotNull
  public static Set<String> extractPermissibleArgumentsFromNumpyDocType(String typeString) {
    List<String> elements = getNumpyUnionType(cleanupOptional(typeString));
    Set<String> result = new LinkedHashSet<String>();
    for (String element : elements) {
      Matcher matcher = QUOTED_STRING_PATTERN.matcher(element);
      if (matcher.matches()) {
        if (matcher.group(1) != null) {
          result.add(matcher.group(1));
        } else if (matcher.group(2) != null) {
          result.add(matcher.group(2));
        }
      }
    }
    return result;
  }

  public static class NotNumpyDocStringException extends Exception {

    public NotNumpyDocStringException(String signature) {
      super("Function " + signature + " is not containing docstring of Numpy format.");
    }
  }

  public static class DocStringParameterBuilder {
    private String myName = "";
    private String myType = "";
    private StringBuilder myDescription = new StringBuilder();

    public void setName(String name) {
      myName = name;
    }

    public void setType(String type) {
      myType = type;
    }

    public void appendDescription(String text) {
      myDescription.append(" ");
      myDescription.append(text);
    }

    public NumPyDocStringParameter build() {
      return new NumPyDocStringParameter(myName, myType, myDescription.toString());
    }
  }
}
