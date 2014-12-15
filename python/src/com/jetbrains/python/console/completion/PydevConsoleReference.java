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

import com.google.common.collect.Maps;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.IToken;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression> {
  private final ConsoleCommunication myCommunication;
  private final String myPrefix;

  public PydevConsoleReference(final PyReferenceExpression expression,
                               final ConsoleCommunication communication,
                               final String prefix) {
    super(expression, true);
    myCommunication = communication;
    myPrefix = prefix;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return new ResolveResult[0];
  }

  @NotNull
  public Object[] getVariants() {
    Map<String, LookupElement> variants = Maps.newHashMap();
    try {
      final List<PydevCompletionVariant> completions = myCommunication.getCompletions(getText(), myPrefix);
      for (PydevCompletionVariant completion : completions) {
        final PsiManager manager = myElement.getManager();
        final String name = completion.getName();
        final int type = completion.getType();
        LookupElementBuilder builder = LookupElementBuilder
          .create(new PydevConsoleElement(manager, name, completion.getDescription()))
          .withIcon(PyCodeCompletionImages.getImageForType(type));


        String args = completion.getArgs();
        if (args.equals("(%)")) {
          builder.withPresentableText("%" + completion.getName());
          builder = builder.withInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              final Editor editor = context.getEditor();
              final Document document = editor.getDocument();
              int offset = context.getStartOffset();
              if (offset == 0 || !"%".equals(document.getText(TextRange.from(offset - 1, 1)))) {
                document.insertString(offset, "%");
              }
            }
          });
          args = "";
        }
        else if (!StringUtil.isEmptyOrSpaces(args)) {
          builder = builder.withTailText(args);
        }
        // Set function insert handler
        if (type == IToken.TYPE_FUNCTION || args.endsWith(")")) {
          builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
        }
        variants.put(name, builder);
      }
    }
    catch (Exception e) {
      //LOG.error(e);
    }
    return variants.values().toArray();
  }

  private String getText() {
    PsiElement element = PsiTreeUtil.getParentOfType(getElement(), PyFile.class);
    if (element != null) {
      return element.getText().replace(CompletionInitializationContext.DUMMY_IDENTIFIER, "");
    }
    return myPrefix;
  }
}
