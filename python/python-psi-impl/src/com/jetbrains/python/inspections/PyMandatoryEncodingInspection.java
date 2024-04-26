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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Function;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * User : catherine
 */
public final class PyMandatoryEncodingInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }
    @Override
    public void visitPyFile(@NotNull PyFile node) {
      if (!(myAllPythons || LanguageLevel.forElement(node).isPython2())) return;

      final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(node);
      if (charsetString == null) {
        TextRange tr = new TextRange(0, 0);
        ProblemsHolder holder = getHolder();
        if (holder != null) {
          holder.registerProblem(node, tr, PyPsiBundle.message("INSP.mandatory.encoding.no.encoding.specified.for.file"),
                                 new AddEncodingQuickFix(myDefaultEncoding, myEncodingFormatIndex));
        }
      }
    }
  }

  public @NlsSafe String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;
  public boolean myAllPythons = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myAllPythons", PyPsiBundle.message("INSP.mandatory.encoding.checkbox.enable.in.python.3")),
      defaultEncodingDropDown(),
      encodingFormatDropDown()
    );
  }

  @NotNull
  static OptDropdown defaultEncodingDropDown() {
    return dropdown("myDefaultEncoding", PyPsiBundle.message("INSP.mandatory.encoding.label.select.default.encoding"),
                    Arrays.asList(PyEncodingUtil.POSSIBLE_ENCODINGS), Function.identity(), Function.identity());
  }

  @NotNull
  static OptDropdown encodingFormatDropDown() {
    return dropdown("myEncodingFormatIndex", PyPsiBundle.message("INSP.mandatory.encoding.label.encoding.comment.format"),
                    EntryStream.of(PyEncodingUtil.ENCODING_FORMAT).mapKeyValue((idx, format) -> option(String.valueOf(idx), format))
                      .toArray(OptDropdown.Option.class));
  }
}
