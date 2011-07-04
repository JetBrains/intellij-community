package com.jetbrains.python.refactoring.move;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Processor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionProcessor extends BaseRefactoringProcessor {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.move.class.or.function");

  private PsiNamedElement[] myElements;
  private PyFile myDestination;

  public PyMoveClassOrFunctionProcessor(Project project, PsiNamedElement[] elements, PyFile destination, boolean previewUsages) {
    super(project);
    assert elements.length > 0;
    myElements = elements;
    myDestination = destination;
    setPreviewUsages(previewUsages);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return myElements;
      }

      @Override
      public String getProcessedElementsHeader() {
        return REFACTORING_NAME;
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> allUsages = new ArrayList<UsageInfo>();
    for (PsiNamedElement element : myElements) {
      final FindUsagesHandler handler = new PyFindUsagesHandlerFactory().createFindUsagesHandler(element, false);
      assert handler != null;
      final List<PsiElement> elementsToProcess = new ArrayList<PsiElement>();
      elementsToProcess.addAll(Arrays.asList(handler.getPrimaryElements()));
      elementsToProcess.addAll(Arrays.asList(handler.getSecondaryElements()));
      for (PsiElement e : elementsToProcess) {
        handler.processElementUsages(e, new Processor<UsageInfo>() {
          @Override
          public boolean process(UsageInfo usageInfo) {
            if (!usageInfo.isNonCodeUsage) {
              allUsages.add(usageInfo);
            }
            return true;
          }
        }, FindUsagesHandler.createFindUsagesOptions(myProject));
      }
    }
    return allUsages.toArray(new UsageInfo[allUsages.size()]);
  }

  @Override
  protected void performRefactoring(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myElements[0].getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            for (PsiNamedElement oldElement: myElements) {
              PyClassRefactoringUtil.rememberNamedReferences(oldElement);
              final PsiNamedElement newElement = (PsiNamedElement)(myDestination.add(oldElement));
              for (UsageInfo usage : usages) {
                final PsiElement oldExpr = usage.getElement();
                // TODO: Respect the qualified import style
                if (oldExpr instanceof PyQualifiedExpression) {
                  final PyQualifiedExpression qexpr = (PyQualifiedExpression)oldExpr;
                  if (qexpr.getQualifier() != null) {
                    final PsiElement newExpr = qexpr.getParent().addBefore(new PyReferenceExpressionImpl(qexpr.getNameElement()), qexpr);
                    qexpr.delete();
                    PyClassRefactoringUtil.insertImport(newExpr, newElement, null, true);
                  }
                }
                PyImportStatementBase importStatement = getUsageImportStatement(usage);
                if (importStatement != null) {
                  PyClassRefactoringUtil.updateImportOfElement(importStatement, newElement);
                }
                if (usage.getFile() == oldElement.getContainingFile()) {
                  PyClassRefactoringUtil.insertImport(oldElement, newElement);
                }
              }
              PyClassRefactoringUtil.restoreNamedReferences(newElement);
              // TODO: Remove extra empty lines after the removed element
              oldElement.delete();
            }
          }
        });
      }
    }, REFACTORING_NAME, null);
  }

  @Nullable
  private static PyImportStatementBase getUsageImportStatement(UsageInfo usage) {
    final PsiReference ref = usage.getReference();
    if (ref != null) {
      return PsiTreeUtil.getParentOfType(ref.getElement(), PyImportStatementBase.class);
    }
    return null;
  }

  @Override
  protected String getCommandName() {
    return REFACTORING_NAME;
  }
}

