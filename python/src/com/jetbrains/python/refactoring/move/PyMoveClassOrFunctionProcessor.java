package com.jetbrains.python.refactoring.move;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
            final PyFile dest = getOrCreateFile(myDestination);
            CommonRefactoringUtil.checkReadOnlyStatus(myProject, dest);
            for (PsiNamedElement e: myElements) {
              // TODO: Check for resulting circular imports
              CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
              assert e instanceof PyClass || e instanceof PyFunction;
              if (e instanceof PyClass && dest.findTopLevelClass(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.class.$0", e.getName()));
              }
              if (e instanceof PyFunction && dest.findTopLevelFunction(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.function.$0", e.getName()));
              }
              checkValidImportableFile(dest, e.getContainingFile().getVirtualFile());
              checkValidImportableFile(e, dest.getVirtualFile());
            }
            for (PsiNamedElement oldElement: myElements) {
              PyClassRefactoringUtil.rememberNamedReferences(oldElement);
              final PsiNamedElement newElement = (PsiNamedElement)(dest.add(oldElement));
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

  private static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
    final PyQualifiedName qName = ResolveImportUtil.findShortestImportableQName(anchor, file);
    if (!PyClassRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.use.module.name.$0", qName));
    }
  }

  @NotNull
  private PyFile getOrCreateFile(String path) {
    final VirtualFile vfile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    final PsiFile psi;
    if (vfile == null) {
      final File file = new File(myDestination);
      try {
        final VirtualFile baseDir = myProject.getBaseDir();
        psi = PyExtractSuperclassHelper.placeFile(myProject,
                                                  StringUtil.notNullize(file.getParent(),
                                                                        baseDir != null ? baseDir.getPath() : "."),
                                                  file.getName());
      }
      catch (IOException e) {
        throw new IncorrectOperationException(String.format("Cannot create file '%s'", myDestination));
      }
    }
    else {
      psi = PsiManager.getInstance(myProject).findFile(vfile);
    }
    if (!(psi instanceof PyFile)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.place.elements.into.nonpython.file"));
    }
    return (PyFile)psi;
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

