package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.actions.ImportFromExistingAction;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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
            return StubIndex.getInstance().getAllKeys(PyClassNameIndex.KEY, project);
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
    public void handleInsert(InsertionContext context, LookupElement item) {
      final PsiReference ref = context.getFile().findReferenceAt(context.getTailOffset() - 1);
      if (ref == null || ref.resolve() == item.getObject()) {
        // no import statement needed
        return;
      }
      PyElement element = (PyElement) ref.getElement();
      boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
      new ImportFromExistingAction(element, (PsiNamedElement) item.getObject(), context.getEditor(), useQualified).execute();
    }
  }

  private static final PyClassNameInsertHandler INSERT_HANDLER = new PyClassNameInsertHandler();
}
