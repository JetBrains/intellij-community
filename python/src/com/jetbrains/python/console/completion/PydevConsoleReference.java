// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.completion;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.IToken;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression> {

  private final ConsoleCommunication myCommunication;
  private final String myPrefix;
  private final boolean myAllowRemoteResolve;

  public PydevConsoleReference(final PyReferenceExpression expression,
                               final ConsoleCommunication communication, final String prefix, boolean allowRemoteResolve) {
    super(expression, true);
    myCommunication = communication;
    myPrefix = prefix;
    myAllowRemoteResolve = allowRemoteResolve;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    if (!myAllowRemoteResolve) {
      return RatedResolveResult.EMPTY_ARRAY;
    }
    PyExpression pyExpression = resolveToDummyDescription();
    if (pyExpression == null) {
      return RatedResolveResult.EMPTY_ARRAY;
    }

    if (pyExpression instanceof PyReferenceExpression) {
      final PsiReference redirectedRef = pyExpression.getReference();
      if (redirectedRef != null) {
        PsiElement resolved = redirectedRef.resolve();
        if (resolved != null) {
          return new ResolveResult[]{new RatedResolveResult(RatedResolveResult.RATE_HIGH, resolved)};
        }
      }
    }

    return RatedResolveResult.EMPTY_ARRAY;

  }

  public PyElement getDocumentationElement() {
    return resolveToDummyDescription();
  }


  private PyExpression resolveToDummyDescription() {
    String qualifiedName = myElement.getText();
    if (qualifiedName == null) {
      return null;
    }
    String description;
    try {

      description = myCommunication.getDescription(qualifiedName);
      if (Strings.isNullOrEmpty(description)) {
        return null;
      }
    }
    catch (Exception e) {
      return null;
    }

    PyElementGenerator generator = PyElementGenerator.getInstance(myElement.getProject());
    PyFile dummyFile = (PyFile)generator.createDummyFile(LanguageLevel.forElement(myElement), description);
    Module module = ModuleUtilCore.findModuleForPsiElement(myElement);
    if (module != null) {
      dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
    }

    List<PyStatement> statements = dummyFile.getStatements();
    PyStatement pyStatement = statements.get(statements.size() - 1);
    return pyStatement instanceof PyExpressionStatement ? ((PyExpressionStatement)pyStatement).getExpression() : null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    Map<String, LookupElement> variants = Maps.newHashMap();
    try {
      final List<PydevCompletionVariant> completions = myCommunication.getCompletions(getText(), myPrefix);
      for (PydevCompletionVariant completion : completions) {
        final PsiManager manager = myElement.getManager();
        final String name = completion.getName();
        final int type = completion.getType();
        LookupElementBuilder builder = LookupElementBuilder.create(new PydevConsoleElement(manager, name, completion.getDescription()))
          .withIcon(PyCodeCompletionImages.getImageForType(type));


        String args = completion.getArgs();
        if (args.equals("(%)")) {
          builder = builder.withPresentableText("%" + completion.getName());
          builder = builder.withInsertHandler(new InsertHandler<>() {
            @Override
            public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
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
