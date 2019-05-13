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
package com.jetbrains.python.console.completion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.impl.LightNamedElement;

/**
 * @author oleg
 * Light element in completion to provide Quick documentation functionality
 */
public class PydevConsoleElement extends LightNamedElement {
  private final String myDescription;

  public PydevConsoleElement(final PsiManager manager, final String name, final String description) {
    super(manager, PythonLanguage.getInstance(), name);
    myDescription = description;
  }

  @Override
  public String toString() {
    return "PydevConsoleElement " + myDescription;
  }

  public static String generateDoc(final PydevConsoleElement pydevConsoleElement) {
    final String description = pydevConsoleElement.myDescription;
    // Description contract:
    // (Signatures\n\n) ? Description
    final int index = description.indexOf("\n\n");
    if (index != -1){
      final StringBuilder builder = new StringBuilder();
      builder.append("<b>").append(description.subSequence(0, index)).append("</b>").append("<hr/>").append(description.substring(index+2));
      return StringUtil.replace(builder.toString(), "\n", "<br/>");
    }
    return StringUtil.replace(description, "\n", "<br/>");
  }
}
