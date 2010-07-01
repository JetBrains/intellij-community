package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.actions.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
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
        final Project project = parameters.getOriginalFile().getProject();
        final GlobalSearchScope scope = ProjectScope.getAllScope(project);
        final Collection<String> allKeys = ApplicationManager.getApplication().runReadAction(new Computable<Collection<String>>() {
          public Collection<String> compute() {
            return PyClassNameIndex.allKeys(project);
          }
        });
        for (final String className : allKeys) {
          if (resultSet.getPrefixMatcher().prefixMatches(className)) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                final Collection<PyClass> classes = PyClassNameIndex.find(className, project, scope);
                for (PyClass aClass : classes) {
                  resultSet.addElement(LookupElementBuilder.create(aClass)
                    .setIcon(aClass.getIcon(Iconable.ICON_FLAG_CLOSED))
                    .setTailText(" " + aClass.getPresentation().getLocationString(), true)
                    .setInsertHandler(INSERT_HANDLER));
                }
              }
            });
          }
        }
      }
    });
  }

  private static class PyClassNameInsertHandler implements InsertHandler<LookupElement> {
    public void handleInsert(final InsertionContext context, final LookupElement item) {
      final PsiReference ref = context.getFile().findReferenceAt(context.getTailOffset() - 1);
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

  private static final PyClassNameInsertHandler INSERT_HANDLER = new PyClassNameInsertHandler();
}
