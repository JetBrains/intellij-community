package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.documentation.DocStringTypeReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionProcessor extends BaseRefactoringProcessor {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.move.class.or.function");

  private PsiNamedElement[] myElements;
  private String myDestination;

  public PyMoveClassOrFunctionProcessor(Project project, PsiNamedElement[] elements, String destination, boolean previewUsages) {
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
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    for (PsiNamedElement element : myElements) {
      usages.addAll(PyRefactoringUtil.findUsages(element));
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected void performRefactoring(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myElements[0].getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final PyFile destination = PyUtil.getOrCreateFile(myDestination, myProject);
            CommonRefactoringUtil.checkReadOnlyStatus(myProject, destination);
            for (PsiNamedElement e: myElements) {
              // TODO: Check for resulting circular imports
              CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
              assert e instanceof PyClass || e instanceof PyFunction;
              if (e instanceof PyClass && destination.findTopLevelClass(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.class.$0", e.getName()));
              }
              if (e instanceof PyFunction && destination.findTopLevelFunction(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.function.$0", e.getName()));
              }
              checkValidImportableFile(destination, e.getContainingFile().getVirtualFile());
              checkValidImportableFile(e, destination.getVirtualFile());
            }
            for (PsiNamedElement oldElement: myElements) {
              moveElement(oldElement, Arrays.asList(usages), destination);
            }
          }
        });
      }
    }, REFACTORING_NAME, null);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  private static void moveElement(@NotNull PsiNamedElement element, @NotNull Collection<UsageInfo> usages, @NotNull PyFile destination) {
    final PsiFile oldFile = element.getContainingFile();
    PyClassRefactoringUtil.rememberNamedReferences(element);
    final PsiNamedElement newElement = (PsiNamedElement)(destination.add(element));
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      // TODO: Respect the qualified import style
      if (usageElement instanceof PyQualifiedExpression) {
        PyQualifiedExpression expr = (PyQualifiedExpression)usageElement;
        if (element instanceof PyClass && PyNames.INIT.equals(expr.getName())) {
          continue;
        }
        if (expr.getQualifier() != null) {
          final PsiElement newExpr = expr.replace(new PyReferenceExpressionImpl(expr.getNameElement()));
          PyClassRefactoringUtil.insertImport(newExpr, newElement, null, true);
        }
      }
      if (usageElement instanceof PyStringLiteralExpression) {
        for (PsiReference ref : usageElement.getReferences()) {
          if (ref instanceof DocStringTypeReference && ref.isReferenceTo(element)) {
            ref.bindToElement(newElement);
          }
        }
      }
      else {
        final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(usageElement, PyImportStatementBase.class);
        if (importStmt != null) {
          PyClassRefactoringUtil.updateImportOfElement(importStmt, newElement);
        }
        if (usage.getFile() == oldFile && (usageElement == null || !PsiTreeUtil.isAncestor(element, usageElement, false))) {
          PyClassRefactoringUtil.insertImport(element, newElement);
        }
        if (usageElement != null && resolvesToLocalStarImport(usageElement)) {
          PyClassRefactoringUtil.insertImport(usageElement, newElement);
          new PyImportOptimizer().processFile(usageElement.getContainingFile()).run();
        }
      }
    }
    PyClassRefactoringUtil.restoreNamedReferences(newElement, element);
    // TODO: Remove extra empty lines after the removed element
    element.delete();
    new PyImportOptimizer().processFile(oldFile).run();
  }

  private static boolean resolvesToLocalStarImport(@NotNull PsiElement element) {
    final PsiReference ref = element.getReference();
    final List<PsiElement> resolvedElements = new ArrayList<PsiElement>();
    if (ref instanceof PsiPolyVariantReference) {
      for (ResolveResult result : ((PsiPolyVariantReference)ref).multiResolve(false)) {
        resolvedElements.add(result.getElement());
      }
    }
    else if (ref != null) {
      resolvedElements.add(ref.resolve());
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      for (PsiElement resolved : resolvedElements) {
        if (resolved instanceof PyStarImportElement && resolved.getContainingFile() == containingFile) {
          return true;
        }
      }
    }
    return false;
  }

  private static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
    final PyQualifiedName qName = ResolveImportUtil.findShortestImportableQName(anchor, file);
    if (!PyClassRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.use.module.name.$0", qName));
    }
  }
}

