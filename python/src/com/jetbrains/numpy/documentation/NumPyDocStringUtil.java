/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import java.util.regex.Pattern;

public class NumPyDocStringUtil {
  private static final String PARAMETERS = "Parameters";
  private static final Pattern NUMPY_PARAMETERS_SECTION = Pattern.compile(PARAMETERS + "[ \\t]*\\n[ \\t]*[-]{" + PARAMETERS.length() + "}");
  private static final String RETURNS = "Returns";
  private static final Pattern NUMPY_RETURNS_SECTION = Pattern.compile(RETURNS + "[ \\t]*\\n[ \\t]*[-]{" + RETURNS.length() + "}");
  private static final String SEE_ALSO = "See Also";
  private static final Pattern NUMPY_SEE_ALSO_SECTION = Pattern.compile(SEE_ALSO + "[ \\t]*\\n[ \\t]*[-]{" + SEE_ALSO.length() + "}");
  private static final String EXAMPLES = "Examples";
  private static final Pattern NUMPY_EXAMPLES_SECTION = Pattern.compile(EXAMPLES + "[ \\t]*\\n[ \\t]*[-]{" + EXAMPLES.length() + "}");

  private NumPyDocStringUtil() {
  }

  public static boolean isNumpyDocString(@NotNull String text) {
    return text.contains("ndarray") ||
           NUMPY_PARAMETERS_SECTION.matcher(text).find() ||
           NUMPY_RETURNS_SECTION.matcher(text).find() ||
           NUMPY_SEE_ALSO_SECTION.matcher(text).find() ||
           NUMPY_EXAMPLES_SECTION.matcher(text).find();
  }
}
