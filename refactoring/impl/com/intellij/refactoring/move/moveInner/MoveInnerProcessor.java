/**
 * created at Sep 24, 2001
 * @author Jeka
 */

package com.intellij.refactoring.move.moveInner;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class MoveInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInner.MoveInnerProcessor");

  private MoveCallback myMoveCallback;

  private PsiClass myInnerClass;
  private PsiClass myOuterClass;
  private PsiElement myTargetContainer;
  private String myParameterNameOuterClass;
  private String myFieldNameOuterClass;
  private String myDescriptiveName = "";
  private String myNewClassName;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private UsageInfo[] myUsagesAfterRefactoring;

  public MoveInnerProcessor(Project project, MoveCallback moveCallback) {
    super(project);
    myMoveCallback = moveCallback;
  }

  public MoveInnerProcessor(Project project,
                            PsiClass innerClass,
                            String name,
                            boolean passOuterClass,
                            String parameterName,
                            final PsiElement targetContainer) {
    super(project);
    setup(innerClass, name, passOuterClass, parameterName, true, true, targetContainer);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.inner.class.command", myDescriptiveName);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveInnerViewDescriptor(myInnerClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetContainer != null);

    Collection<PsiReference> innerClassRefs = ReferencesSearch.search(myInnerClass).findAll();
    ArrayList<UsageInfo> usageInfos = new ArrayList<UsageInfo>(innerClassRefs.size());
    for (PsiReference innerClassRef : innerClassRefs) {
      PsiElement ref = innerClassRef.getElement();
      if (!PsiTreeUtil.isAncestor(myInnerClass, ref, true)) { // do not show self-references
        usageInfos.add(new UsageInfo(ref));
      }
    }

    final String newQName;
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiDirectory targetDirectory = ((PsiDirectory)myTargetContainer);
      final PsiPackage aPackage = targetDirectory.getPackage();
      LOG.assertTrue(aPackage != null);
      newQName = aPackage.getQualifiedName() + "." + myNewClassName;
    }
    else if (myTargetContainer instanceof PsiClass) {
      final String qName = ((PsiClass)myTargetContainer).getQualifiedName();
      if (qName != null) {
        newQName = qName + "." + myNewClassName;
      }
      else {
        newQName = myNewClassName;
      }
    }
    else {
      newQName = myNewClassName;
    }
    MoveClassesOrPackagesUtil.findNonCodeUsages(mySearchInComments, mySearchInNonJavaFiles,
                                                myInnerClass, newQName, usageInfos);
    return usageInfos.toArray(new UsageInfo[usageInfos.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiClass;
    LOG.assertTrue(condition);
    myInnerClass = (PsiClass)elements[0];
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public void setSearchInComments(boolean searchInComments) {
    mySearchInComments = searchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }


  protected void performRefactoring(final UsageInfo[] usages) {
    PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = manager.getElementFactory();

    final RefactoringElementListener elementListener = getTransaction().getElementListener(myInnerClass);
    String newClassName = myNewClassName;
    try {
      PsiField field = null;
      if (myParameterNameOuterClass != null) {
        // pass outer as a parameter
        field = factory.createField(myFieldNameOuterClass, factory.createType(myOuterClass));
        field = (PsiField)myInnerClass.add(field);
        addFieldInitializationToConstructors(myInnerClass, field, myParameterNameOuterClass);
      }

      ChangeContextUtil.encodeContextInfo(myInnerClass, false);

      PsiClass newClass;
      if (myTargetContainer instanceof PsiDirectory) {
        newClass = ((PsiDirectory)myTargetContainer).createClass(newClassName);
        PsiDocComment defaultDocComment = newClass.getDocComment();
        if (defaultDocComment != null && myInnerClass.getDocComment() == null) {
          myInnerClass.addAfter(defaultDocComment, null);
        }

        newClass = (PsiClass)newClass.replace(myInnerClass);
        newClass.getModifierList().setModifierProperty(PsiModifier.STATIC, false);
        newClass.getModifierList().setModifierProperty(PsiModifier.PRIVATE, false);
        newClass.getModifierList().setModifierProperty(PsiModifier.PROTECTED, false);
        if (myOuterClass.isInterface()) {
          newClass.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        }

      }
      else {
        newClass = (PsiClass)myTargetContainer.add(myInnerClass);
      }
      newClass.setName(newClassName);

      // replace references in a new class to old inner class with references to itself
      for (PsiReference ref : ReferencesSearch.search(myInnerClass, new LocalSearchScope(newClass), true).findAll()) {
        PsiElement element = ref.getElement();
        if (element.getParent() instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement)element.getParent();
          PsiElement parentRefElement = parentRef.resolve();
          if (parentRefElement instanceof PsiClass) { // reference to inner class inside our inner
            parentRef.getQualifier().delete();
            continue;
          }
        }
        ref.bindToElement(newClass);
      }

      myInnerClass.delete();

      // correct references in usages
      for (UsageInfo usage : usages) {
        if (usage.isNonCodeUsage) continue;
        PsiElement ref = usage.getElement();
        if (myParameterNameOuterClass != null) { // should pass outer as parameter
          PsiElement refParent = ref.getParent();
          if (refParent instanceof PsiNewExpression || refParent instanceof PsiAnonymousClass) {
            PsiNewExpression newExpr = refParent instanceof PsiNewExpression
                                       ? (PsiNewExpression)refParent
                                       : (PsiNewExpression)refParent.getParent();

            PsiExpressionList argList = newExpr.getArgumentList();

            if (argList != null) { // can happen in incomplete code
              if (newExpr.getQualifier() == null) {
                PsiThisExpression thisExpr;
                PsiClass parentClass = RefactoringUtil.getThisClass(newExpr);
                if (myOuterClass.equals(parentClass)) {
                  thisExpr = RefactoringUtil.createThisExpression(manager, null);
                }
                else {
                  thisExpr = RefactoringUtil.createThisExpression(manager, myOuterClass);
                }
                argList.addAfter(thisExpr, null);
              }
              else {
                argList.addAfter(newExpr.getQualifier(), null);
                newExpr.getQualifier().delete();
              }
            }
          }
        }
        ref.getReference().bindToElement(newClass);
      }

      if (field != null) {
        PsiExpression accessExpression = factory.createExpressionFromText(myFieldNameOuterClass, null);
        ChangeContextUtil.decodeContextInfo(newClass, myOuterClass, accessExpression);
      }
      else {
        ChangeContextUtil.decodeContextInfo(newClass, null, null);
      }

      PsiFile targetFile = newClass.getContainingFile();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, targetFile.getVirtualFile(), newClass.getTextOffset());
      FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
      elementListener.elementMoved(newClass);

      myUsagesAfterRefactoring = usages;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void performPsiSpoilingRefactoring() {
    if (myUsagesAfterRefactoring != null) {
      RefactoringUtil.renameNonCodeUsages(myProject, myUsagesAfterRefactoring);
    }
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final ArrayList<String> conflicts = new ArrayList<String>();

    class Visitor extends PsiRecursiveElementVisitor {
      private final HashMap<PsiElement,HashSet<PsiElement>> reported = new HashMap<PsiElement, HashSet<PsiElement>>();

      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiMember &&
            PsiTreeUtil.isAncestor(myInnerClass, resolved, true) &&
            becomesInaccessible(((PsiMember)resolved))) {
          final PsiElement container = ConflictsUtil.getContainer(reference);
          HashSet<PsiElement> containerSet = reported.get(resolved);
          if (containerSet == null) {
            containerSet = new HashSet<PsiElement>();
            reported.put(resolved, containerSet);
          }
          if (!containerSet.contains(container)) {
            containerSet.add(container);
            String message = RefactoringBundle.message("0.will.become.inaccessible.from.1",
                                                       ConflictsUtil.getDescription(resolved, true),
                                                       ConflictsUtil.getDescription(container, true));
            conflicts.add(message);
          }
        }
      }

      private boolean becomesInaccessible(PsiMember element) {
        final String visibilityModifier = VisibilityUtil.getVisibilityModifier(element.getModifierList());
        if (PsiModifier.PRIVATE.equals(visibilityModifier)) return true;
        if (PsiModifier.PUBLIC.equals(visibilityModifier)) return false;
        final PsiFile containingFile = myOuterClass.getContainingFile();
        if (myTargetContainer instanceof PsiDirectory) {
          final PsiPackage aPackage = ((PsiDirectory)myTargetContainer).getPackage();
          return !isInPackage(containingFile, aPackage);
        }
        else { // target container is a class
          PsiFile targetFile = myTargetContainer.getContainingFile();
          if (targetFile != null) {
            final PsiDirectory containingDirectory = targetFile.getContainingDirectory();
            if (containingDirectory != null) {
              final PsiPackage targetPackage = containingDirectory.getPackage();
              return isInPackage(containingFile, targetPackage);
            }
          }
        }
        return false;
      }


      public void visitClass(PsiClass aClass) {
        if (aClass == myInnerClass) return;
        super.visitClass(aClass);
      }
    }

//    if (myInnerClass.hasModifierProperty(PsiModifier.)) {
    myOuterClass.accept(new Visitor());

    return showConflicts(conflicts);
  }

  private static boolean isInPackage(final PsiFile containingFile, PsiPackage aPackage) {
    if (containingFile != null) {
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        PsiPackage filePackage = containingDirectory.getPackage();
        if (filePackage != null && !filePackage.getQualifiedName().equals(
          aPackage.getQualifiedName())) {
          return false;
        }
      }
    }
    return true;
  }

  public void run(final MoveInnerDialog dialog) {
    myPrepareSuccessfulSwingThreadCallback = new Runnable() {
      public void run() {
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    };

    final String className = dialog.getClassName();
    final PsiClass innerClass = dialog.getInnerClass();
    final boolean passOuterClass = dialog.isPassOuterClass();
    final String parameterName = dialog.getParameterName();
    final PsiElement targetContainer = dialog.getTargetContainer();

    setup(innerClass, className, passOuterClass, parameterName,
          dialog.isSearchInComments(), dialog.isSearchInNonJavaFiles(), targetContainer);

    run();
  }

  public void setup(final PsiClass innerClass,
                    final String className,
                    final boolean passOuterClass,
                    final String parameterName,
                    boolean searchInComments,
                    boolean searchInNonJava,
                    final @NotNull PsiElement targetContainer) {
    myNewClassName = className;
    myInnerClass = innerClass;
    myDescriptiveName = UsageViewUtil.getDescriptiveName(myInnerClass);
    myOuterClass = myInnerClass.getContainingClass();
    myTargetContainer = targetContainer;
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    myParameterNameOuterClass = passOuterClass ? parameterName : null;
    if (myParameterNameOuterClass != null) {
      myFieldNameOuterClass =
      codeStyleManager.variableNameToPropertyName(myParameterNameOuterClass, VariableKind.PARAMETER);
      myFieldNameOuterClass = codeStyleManager.propertyNameToVariableName(myFieldNameOuterClass, VariableKind.FIELD);
    }
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJava;
  }

  private void addFieldInitializationToConstructors(PsiClass aClass, PsiField field, String parameterName)
    throws IncorrectOperationException {

    PsiMethod[] constructors = aClass.getConstructors();
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        if (parameterName != null) {
          PsiParameterList parameterList = constructor.getParameterList();
          PsiParameter parameter = factory.createParameter(parameterName, field.getType());
          parameterList.addAfter(parameter, null);
        }
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          PsiStatement first = statements[0];
          if (first instanceof PsiExpressionStatement) {
            PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
            if (expression instanceof PsiMethodCallExpression) {
              @NonNls String text = ((PsiMethodCallExpression)expression).getMethodExpression().getText();
              if ("this".equals(text)) {
                continue;
              }
            }
          }
        }
        createAssignmentStatement(constructor, field.getName(), parameterName);
      }
    }
    else {
      PsiMethod constructor = factory.createConstructor();
      if (parameterName != null) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter parameter = factory.createParameter(parameterName, field.getType());
        parameterList.add(parameter);
      }
      createAssignmentStatement(constructor, field.getName(), parameterName);
      aClass.add(constructor);
    }
  }

  private PsiStatement createAssignmentStatement(PsiMethod constructor, String fieldname, String parameterName)
    throws IncorrectOperationException {

    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    @NonNls String pattern = fieldname + "=a;";
    if (fieldname.equals(parameterName)) {
      pattern = "this." + pattern;
    }

    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
    statement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(statement);

    PsiCodeBlock body = constructor.getBody();
    statement = (PsiExpressionStatement)body.addAfter(statement, getAnchorElement(body));

    PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
    PsiReferenceExpression rExpr = (PsiReferenceExpression)assignment.getRExpression();
    PsiIdentifier identifier = (PsiIdentifier)rExpr.getReferenceNameElement();
    identifier.replace(factory.createIdentifier(parameterName));
    return statement;
  }

  private static PsiElement getAnchorElement(PsiCodeBlock body) {
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      PsiStatement first = statements[0];
      if (first instanceof PsiExpressionStatement) {

        PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodCall = ((PsiMethodCallExpression)expression).getMethodExpression();
          @NonNls String text = methodCall.getText();
          if ("super".equals(text)) {
            return first;
          }
        }
      }
    }
    return null;
  }

  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  public String getNewClassName() {
    return myNewClassName;
  }

  public boolean shouldPassParameter() {
    return myParameterNameOuterClass != null;
  }


  public String getParameterName() {
    return myParameterNameOuterClass;
  }
}
