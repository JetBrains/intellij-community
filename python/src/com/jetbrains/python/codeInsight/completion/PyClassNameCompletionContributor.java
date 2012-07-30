package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;

import java.util.*;

/**
 * @author yole
 */
public class PyClassNameCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.isExtendedCompletion()) {
      final PsiElement element = parameters.getPosition();
      final PsiElement parent = element.getParent();
      if (parent instanceof PyReferenceExpression && ((PyReferenceExpression)parent).getQualifier() != null) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
        return;
      }
      addVariantsFromIndex(result, parameters.getOriginalFile(), PyClassNameIndex.KEY, CLASS_INSERT_HANDLER,
                           Conditions.<PyClass>alwaysTrue());
      addVariantsFromIndex(result, parameters.getOriginalFile(), PyFunctionNameIndex.KEY, FUNCTION_INSERT_HANDLER, TOPLEVEL_FUNCTION);
    }
  }

  private static Condition<PyFunction> TOPLEVEL_FUNCTION = new Condition<PyFunction>() {
    @Override
    public boolean value(PyFunction pyFunction) {
      return PyPsiUtils.isTopLevel(pyFunction);
    }
  };

  private static <T extends PsiNamedElement> void addVariantsFromIndex(final CompletionResultSet resultSet,
                                                                       final PsiFile targetFile,
                                                                       final StubIndexKey<String, T> key,
                                                                       final InsertHandler<LookupElement> insertHandler,
                                                                       final Condition<T> condition) {
    final Project project = targetFile.getProject();
    final GlobalSearchScope scope = PyClassNameIndex.projectWithLibrariesScope(project);

    Collection<String> keys = StubIndex.getInstance().getAllKeys(key, project);
    final List<String> allNames = new ArrayList<String>();
    for (String s : keys) {
      if (resultSet.getPrefixMatcher().prefixMatches(s)) {
        allNames.add(s);
      }
    }
    for (final String elementName : CompletionUtil.sortForCompletion(resultSet.getPrefixMatcher(), allNames)) {
      for (T element : StubIndex.getInstance().get(key, elementName, project, scope)) {
        if (condition.value(element)) {
          resultSet.addElement(LookupElementBuilder.create(element)
                                 .withIcon(element.getIcon(Iconable.ICON_FLAG_CLOSED))
                                 .withTailText(" " + ((NavigationItem)element).getPresentation().getLocationString(), true)
                                 .withInsertHandler(insertHandler));
        }
      }
    }
  }

  private static final InsertHandler<LookupElement> CLASS_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    public void handleInsert(final InsertionContext context, final LookupElement item) {
      addImportForLookupElement(context, item, context.getTailOffset() - 1);
    }
  };


  private static final InsertHandler<LookupElement> FUNCTION_INSERT_HANDLER = new PyFunctionInsertHandler() {
    public void handleInsert(final InsertionContext context, final LookupElement item) {
      int tailOffset = context.getTailOffset()-1;
      super.handleInsert(context, item);  // adds parentheses, modifies tail offset
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(context.getProject());
      documentManager.commitDocument(documentManager.getDocument(context.getFile()));
      addImportForLookupElement(context, item, tailOffset);
    }
  };

  private static void addImportForLookupElement(final InsertionContext context, final LookupElement item, final int tailOffset) {
    final PsiReference ref = context.getFile().findReferenceAt(tailOffset);
    if (ref == null || ref.resolve() == item.getObject()) {
      // no import statement needed
      return;
    }
    new WriteCommandAction(context.getProject(), context.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        AddImportHelper.addImport((PsiNamedElement)item.getObject(), context.getFile(), (PyElement)ref.getElement());
      }
    }.execute();
  }
}
