/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.psi.types.PyModuleType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyClassNameCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.isExtendedCompletion()) {
      final PsiElement element = parameters.getPosition();
      final PsiElement parent = element.getParent();
      if (parent instanceof PyReferenceExpression && ((PyReferenceExpression)parent).isQualified()) {
        return;
      }
      if (parent instanceof PyStringLiteralExpression) {
        String prefix = parent.getText().substring(0, parameters.getOffset() - parent.getTextRange().getStartOffset());
        if (prefix.contains(".")) {
          return;
        }
      }
      final FileViewProvider provider = element.getContainingFile().getViewProvider();
      if (provider instanceof MultiplePsiFilesPerDocumentFileViewProvider) return;
      if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
        return;
      }
      final PsiFile originalFile = parameters.getOriginalFile();
      addVariantsFromIndex(result, originalFile, PyClassNameIndex.KEY,
                           parent instanceof PyStringLiteralExpression ? STRING_LITERAL_INSERT_HANDLER : IMPORTING_INSERT_HANDLER,
                           Conditions.<PyClass>alwaysTrue(), PyClass.class);
      addVariantsFromIndex(result, originalFile, PyFunctionNameIndex.KEY,
                           getFunctionInsertHandler(parent), IS_TOPLEVEL, PyFunction.class);
      addVariantsFromIndex(result, originalFile, PyVariableNameIndex.KEY,
                           parent instanceof PyStringLiteralExpression ? STRING_LITERAL_INSERT_HANDLER : IMPORTING_INSERT_HANDLER,
                           IS_TOPLEVEL, PyTargetExpression.class);
      addVariantsFromModules(result, originalFile, parent instanceof PyStringLiteralExpression);
    }
  }

  private static InsertHandler<LookupElement> getFunctionInsertHandler(PsiElement parent) {
    if (parent instanceof PyStringLiteralExpression) {
      return STRING_LITERAL_INSERT_HANDLER;
    }
    if (parent.getParent() instanceof PyDecorator) {
      return IMPORTING_INSERT_HANDLER;
    }
    return FUNCTION_INSERT_HANDLER;
  }

  private static void addVariantsFromModules(CompletionResultSet result, PsiFile targetFile, boolean inStringLiteral) {
    Collection<VirtualFile> files = FileTypeIndex.getFiles(PythonFileType.INSTANCE, PyProjectScopeBuilder.excludeSdkTestsScope(targetFile));
    for (VirtualFile file : files) {
      PsiFile pyFile = targetFile.getManager().findFile(file);
      PsiFileSystemItem importable = (PsiFileSystemItem) PyUtil.turnInitIntoDir(pyFile);
      if (importable == null) continue;
      if (PythonImportUtils.isImportableModule(targetFile, importable)) {
        LookupElementBuilder element = PyModuleType.buildFileLookupElement(importable, null);
        if (element != null) {
          result.addElement(element.withInsertHandler(inStringLiteral ? STRING_LITERAL_INSERT_HANDLER : IMPORTING_INSERT_HANDLER));
        }
      }
    }
  }

  private static Condition<PsiElement> IS_TOPLEVEL = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return PyUtil.isTopLevel(element);
    }
  };

  private static <T extends PsiNamedElement> void addVariantsFromIndex(final CompletionResultSet resultSet,
                                                                       final PsiFile targetFile,
                                                                       final StubIndexKey<String, T> key,
                                                                       final InsertHandler<LookupElement> insertHandler,
                                                                       final Condition<? super T> condition, Class<T> elementClass) {
    final Project project = targetFile.getProject();
    GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(targetFile);

    Collection<String> keys = StubIndex.getInstance().getAllKeys(key, project);
    for (final String elementName : CompletionUtil.sortMatching(resultSet.getPrefixMatcher(), keys)) {
      for (T element : StubIndex.getElements(key, elementName, project, scope, elementClass)) {
        if (condition.value(element)) {
          final String name = element.getName();
          if (name != null) {
            resultSet.addElement(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(element.getIcon(0))
                                   .withTailText(" " + ((NavigationItem)element).getPresentation().getLocationString(), true)
                                   .withInsertHandler(insertHandler));
          }
        }
      }
    }
  }

  private static final InsertHandler<LookupElement> IMPORTING_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    public void handleInsert(final InsertionContext context, final LookupElement item) {
        addImportForLookupElement(context, item, context.getTailOffset() - 1);
    }
  };


  private static final InsertHandler<LookupElement> FUNCTION_INSERT_HANDLER = new PyFunctionInsertHandler() {
    public void handleInsert(@NotNull final InsertionContext context, @NotNull final LookupElement item) {
      int tailOffset = context.getTailOffset()-1;
      super.handleInsert(context, item);  // adds parentheses, modifies tail offset
      context.commitDocument();
      addImportForLookupElement(context, item, tailOffset);
    }
  };

  private static final InsertHandler<LookupElement> STRING_LITERAL_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      PsiElement element = item.getPsiElement();
      if (element instanceof PyQualifiedNameOwner) {
        String qName = ((PyQualifiedNameOwner) element).getQualifiedName();
        String name = ((PyQualifiedNameOwner) element).getName();
        if (qName != null && name != null) {
          String qNamePrefix = qName.substring(0, qName.length()-name.length());
          context.getDocument().insertString(context.getStartOffset(), qNamePrefix);
        }
      }
    }
  };

  private static void addImportForLookupElement(final InsertionContext context, final LookupElement item, final int tailOffset) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(context.getProject());
    Document document = manager.getDocument(context.getFile());
    if (document != null) {
      manager.commitDocument(document);
    }
    final PsiReference ref = context.getFile().findReferenceAt(tailOffset);
    if (ref == null || ref.resolve() == item.getPsiElement()) {
      // no import statement needed
      return;
    }
    new WriteCommandAction(context.getProject(), context.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        AddImportHelper.addImport(PyUtil.as(item.getPsiElement(), PsiNamedElement.class), context.getFile(), (PyElement)ref.getElement());
      }
    }.execute();
  }
}
