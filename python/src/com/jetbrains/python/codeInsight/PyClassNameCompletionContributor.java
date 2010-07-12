package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.actions.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PyClassNameCompletionContributor extends CompletionContributor {
  public PyClassNameCompletionContributor() {
    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>(false) {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet resultSet) {
        addVariantsFromIndex(resultSet, parameters.getOriginalFile(), PyClassNameIndex.KEY, CLASS_INSERT_HANDLER, Condition.TRUE);
        addVariantsFromIndex(resultSet, parameters.getOriginalFile(), PyFunctionNameIndex.KEY, FUNCTION_INSERT_HANDLER, TOPLEVEL_FUNCTION);
      }
    });
  }

  private static Condition<PyFunction> TOPLEVEL_FUNCTION = new Condition<PyFunction>() {
    @Override
    public boolean value(PyFunction pyFunction) {
      return pyFunction.isTopLevel();
    }
  };

  private static <T extends PsiNamedElement> void addVariantsFromIndex(final CompletionResultSet resultSet,
                                                                       final PsiFile targetFile,
                                                                       final StubIndexKey<String, T> key,
                                                                       final InsertHandler<LookupElement> insertHandler,
                                                                       final Condition<T> condition) {
    final Project project = targetFile.getProject();
    final GlobalSearchScope scope = ProjectScope.getAllScope(project);
    final Collection<String> allNames = ApplicationManager.getApplication().runReadAction(new Computable<Collection<String>>() {
      public Collection<String> compute() {
        return StubIndex.getInstance().getAllKeys(key, project);
      }
    });
    for (final String elementName : allNames) {
      if (resultSet.getPrefixMatcher().prefixMatches(elementName)) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Collection<T> elements = StubIndex.getInstance().get(key, elementName, project, scope);
            for (T element : elements) {
              if (condition.value(element)) {
                resultSet.addElement(LookupElementBuilder.create(element)
                                       .setIcon(element.getIcon(Iconable.ICON_FLAG_CLOSED))
                                       .setTailText(" " + ((NavigationItem)element).getPresentation().getLocationString(), true)
                                       .setInsertHandler(insertHandler));
              }
            }
          }
        });
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
        addImport((PsiNamedElement) item.getObject(), context.getFile(), (PyElement) ref.getElement());
      }
    }.execute();
  }

  private static void addImport(final PsiNamedElement target, final PsiFile file, final PyElement element) {
    final boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    final String path = ResolveImportUtil.findShortestImportableName(element, target.getContainingFile().getVirtualFile());
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
    if (useQualified) {
      AddImportHelper.addImportStatement(file, path, null);
      element.replace(elementGenerator.createExpressionFromText(path + "." + target.getName()));
    }
    else {
      final List<PyFromImportStatement> existingImports = ((PyFile)file).getFromImports();
      for (PyFromImportStatement existingImport : existingImports) {
        final PyQualifiedName qName = existingImport.getImportSourceQName();
        if (qName != null && qName.toString().equals(path)) {
          PyImportElement importElement = elementGenerator.createImportElement(target.getName());
          existingImport.add(importElement);
          return;
        }
      }
      AddImportHelper.addImportFromStatement(file, path, target.getName(), null);
    }
  }

}
