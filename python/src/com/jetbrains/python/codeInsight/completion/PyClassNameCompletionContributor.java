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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.PythonReferenceImporter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.PyModuleType;

import java.util.Collection;

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
      addVariantsFromIndex(result, parameters.getOriginalFile(), PyClassNameIndex.KEY, IMPORTING_INSERT_HANDLER,
                           Conditions.<PyClass>alwaysTrue());
      addVariantsFromIndex(result, parameters.getOriginalFile(), PyFunctionNameIndex.KEY, FUNCTION_INSERT_HANDLER, TOPLEVEL_FUNCTION);
      addVariantsFromModules(result, parameters.getOriginalFile());
    }
  }

  private static void addVariantsFromModules(CompletionResultSet result, PsiFile targetFile) {
    Collection<VirtualFile> files = FileTypeIndex.getFiles(PythonFileType.INSTANCE, PyProjectScopeBuilder.excludeSdkTestsScope(targetFile));
    for (VirtualFile file : files) {
      PsiFile pyFile = targetFile.getManager().findFile(file);
      if (pyFile == null) continue;
      PsiFileSystemItem importable = (PsiFileSystemItem) PyUtil.turnInitIntoDir(pyFile);
      if (PythonReferenceImporter.isImportableModule(targetFile, importable)) {
        LookupElementBuilder element = PyModuleType.buildFileLookupElement(importable, null);
        if (element != null) {
          result.addElement(element.withInsertHandler(IMPORTING_INSERT_HANDLER));
        }
      }
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
    GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(targetFile);

    Collection<String> keys = StubIndex.getInstance().getAllKeys(key, project);
    for (final String elementName : CompletionUtil.sortMatching(resultSet.getPrefixMatcher(), keys)) {
      for (T element : StubIndex.getInstance().get(key, elementName, project, scope)) {
        if (condition.value(element)) {
          resultSet.addElement(LookupElementBuilder.createWithIcon(element)
                                 .withTailText(" " + ((NavigationItem)element).getPresentation().getLocationString(), true)
                                 .withInsertHandler(insertHandler));
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
