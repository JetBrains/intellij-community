package com.intellij.refactoring.anonymousToInner;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class AnonymousToInnerHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler");

  private static final String REFACTORING_NAME = "Convert Anonymous to Inner";

  private Project myProject;

  private PsiManager myManager;

  private PsiAnonymousClass myAnonClass;
  private PsiClass myTargetClass;

  private VariableInfo[] myVariableInfos;
  private AnonymousToInnerDialog myDialog;
  private boolean myMakeStatic;

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
  }

  public void invoke(final Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    if (!file.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, file);
      return;
    }

    final int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiAnonymousClass anonymousClass = findAnonymousClass(file, offset);
    if (anonymousClass == null) {
      RefactoringMessageUtil.showErrorMessage(
              REFACTORING_NAME,
              "Cannot perform the refactoring.\nThe caret should be positioned inside the anonymous class.",
              HelpID.ANONYMOUS_TO_INNER,
              project);
      return;
    }
    invoke(project, anonymousClass);
  }

  public void invoke(final Project project, final PsiAnonymousClass anonymousClass) {
    myProject = project;

    myManager = PsiManager.getInstance(myProject);
    myAnonClass = anonymousClass;

    PsiClassType baseRef = myAnonClass.getBaseClassType();

    if (baseRef.resolve() == null) {
      String message = "Cannot resolve " + baseRef.getCanonicalText();
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ANONYMOUS_TO_INNER, project);
      return;
    }
    PsiElement targetContainer = findTargetContainer(myAnonClass);
    if (targetContainer instanceof JspFile) {
      String message = REFACTORING_NAME + " refactoring is not supported for JSP";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.ANONYMOUS_TO_INNER, project);
      return;
    }
    boolean condition = targetContainer instanceof PsiClass;
    LOG.assertTrue(condition);
    myTargetClass = (PsiClass) targetContainer;

    if (!myTargetClass.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, myTargetClass);
      return;
    }

    HashMap<PsiVariable,VariableInfo> variableInfoMap = new HashMap<PsiVariable, VariableInfo>();
    ArrayList<VariableInfo> variableInfoVector = new ArrayList<VariableInfo>();
    collectUsedVariables(variableInfoMap, variableInfoVector, myAnonClass);
    myVariableInfos = variableInfoVector.toArray(new VariableInfo[variableInfoVector.size()]);
    final boolean needsThis = needsThis() || PsiUtil.isInnerClass(myTargetClass);
    myDialog = new AnonymousToInnerDialog(
            myProject,
            myAnonClass,
            myVariableInfos,
            needsThis);
    myDialog.show();
    if (!myDialog.isOK()) {
      return;
    }
    myVariableInfos = myDialog.getVariableInfos();
    myMakeStatic = myDialog.isMakeStatic();

    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
              public void run() {
                final Runnable action = new Runnable() {
                  public void run() {
                    try {
                      doRefactoring();
                    } catch (IncorrectOperationException e) {
                      LOG.error(e);
                    }
                  }
                };
                ApplicationManager.getApplication().runWriteAction(action);
              }
            },
            REFACTORING_NAME,
            null
    );

  }

  private void doRefactoring() throws IncorrectOperationException {
    PsiClass aClass = createClass(myDialog.getClassName());
    myTargetClass.add(aClass);

    PsiNewExpression newExpr = (PsiNewExpression) myAnonClass.getParent();
    StringBuffer buf = new StringBuffer("new ");
    buf.append(aClass.getName());
    buf.append("(");
    boolean isFirstParameter = true;
    for (int idx = 0; idx < myVariableInfos.length; idx++) {
      VariableInfo info = myVariableInfos[idx];
      if (info.passAsParameter) {
        if (isFirstParameter) {
          isFirstParameter = false;
        } else {
          buf.append(",");
        }
        buf.append(info.variable.getName());
      }
    }
    buf.append(")");
    PsiExpression newClassExpression = myManager.getElementFactory().createExpressionFromText(buf.toString(), null);
    newClassExpression = (PsiExpression) CodeStyleManager.getInstance(myProject).reformat(newClassExpression);
    newExpr.replace(newClassExpression);
  }

  private PsiAnonymousClass findAnonymousClass(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    while (element != null) {
      if (element instanceof PsiAnonymousClass) {
        return (PsiAnonymousClass) element;
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiElement findTargetContainer(PsiAnonymousClass anonClass) {
    PsiElement parent = anonClass.getParent();
    while (true) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        return parent;
      }
      parent = parent.getParent();
    }
  }

  private void collectUsedVariables(HashMap<PsiVariable,VariableInfo> variableInfoMap, ArrayList<VariableInfo> variableInfoVector, PsiElement scope) {
    if (scope instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) scope;
      if (refExpr.getQualifierExpression() == null) {
        PsiElement refElement = refExpr.resolve();
        if (refElement instanceof PsiVariable) {
          PsiVariable var = (PsiVariable) refElement;
          PsiElement parent = var.getParent();
          while (true) {
            if (parent instanceof PsiFile) break;
            if (myAnonClass.equals(parent)) break;
            if (myTargetClass.equals(parent.getParent())) {
              saveVariable(variableInfoMap, variableInfoVector, var, refExpr);
              break;
            }
            parent = parent.getParent();
          }
        }
      }
    }

    PsiElement[] children = scope.getChildren();
    for (int idx = 0; idx < children.length; idx++) {
      collectUsedVariables(variableInfoMap, variableInfoVector, children[idx]);
    }
  }

  private Boolean cachedNeedsThis = null;
  private boolean needsThis() {
    if(cachedNeedsThis == null) {

      ElementNeedsThis memberNeedsThis = new ElementNeedsThis(myTargetClass, myAnonClass);
      myAnonClass.accept(memberNeedsThis);
      class HasExplicitThis extends PsiRecursiveElementVisitor {
        boolean hasExplicitThis = false;
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        public void visitThisExpression(PsiThisExpression expression) {
          hasExplicitThis = true;
        }
      }
      final HasExplicitThis hasExplicitThis = new HasExplicitThis();
      ((PsiNewExpression) myAnonClass.getParent()).getArgumentList().accept(hasExplicitThis);
      cachedNeedsThis = new Boolean(memberNeedsThis.usesMembers() || hasExplicitThis.hasExplicitThis);
    }
    return cachedNeedsThis.booleanValue();
  }


  private void saveVariable(HashMap<PsiVariable,VariableInfo> variableInfoMap, ArrayList<VariableInfo> variableInfoVector, PsiVariable var, PsiReferenceExpression usage) {
    VariableInfo info = variableInfoMap.get(var);
    if (info == null) {
      info = new VariableInfo(var);
      variableInfoMap.put(var, info);
      variableInfoVector.add(info);
    }
    if (!isUsedInInitializer(usage)) {
      info.saveInField = true;
    }
  }

  private boolean isUsedInInitializer(PsiElement usage) {
    PsiElement parent = usage.getParent();
    while (!myAnonClass.equals(parent)) {
      if (parent instanceof PsiExpressionList) {
        PsiExpressionList expressionList = (PsiExpressionList) parent;
        if (myAnonClass.equals(expressionList.getParent())) {
          return true;
        }
      } else if (parent instanceof PsiClassInitializer && myAnonClass.equals(((PsiClassInitializer)parent).getContainingClass())) {
        //class initializers will be moved to constructor to be generated
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  private PsiClass createClass(String name) throws IncorrectOperationException {
    PsiElementFactory factory = myAnonClass.getManager().getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    final PsiNewExpression newExpression = (PsiNewExpression) myAnonClass.getParent();
    final PsiMethod superConstructor = newExpression.resolveConstructor();

    PsiClass aClass = factory.createClass(name);
    if (!myTargetClass.isInterface()) {
      aClass.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
    }
    PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(myAnonClass, PsiModifierListOwner.class);
    if (owner != null && owner.hasModifierProperty(PsiModifier.STATIC)) {
      aClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }
    PsiJavaCodeReferenceElement baseClassRef = myAnonClass.getBaseClassReference();
    PsiClass baseClass = (PsiClass)baseClassRef.resolve();
    if (baseClass != null && baseClass.isInterface()) {
      aClass.getImplementsList().add(baseClassRef);
    }
    else {
      aClass.getExtendsList().add(baseClassRef);
    }

    renameReferences(myAnonClass);
    copyClassBody(myAnonClass, aClass, myVariableInfos.length > 0);

    if (myVariableInfos.length > 0) {
      createFields(aClass);
    }

    PsiExpressionList exprList = newExpression.getArgumentList();
    PsiExpression[] originalExpressions = exprList.getExpressions();
    final PsiReferenceList superConstructorThrowsList =
            superConstructor != null && superConstructor.getThrowsList().getReferencedTypes().length > 0
            ? superConstructor.getThrowsList()
            : null;
    if (myVariableInfos.length > 0 || originalExpressions.length > 0 || superConstructorThrowsList != null) {
      PsiMethod constructor = factory.createConstructor();
      if (superConstructorThrowsList != null) {
        constructor.getThrowsList().replace(superConstructorThrowsList);
      }
      if (originalExpressions.length > 0) {
        createSuperStatement(constructor, originalExpressions);
      }
      if (myVariableInfos.length > 0) {
        fillParameterList(constructor);
        createAssignmentStatements(constructor);

        appendClassInitializers(constructor);
      }

      constructor = (PsiMethod) codeStyleManager.reformat(constructor);
      aClass.add(constructor);
    }

    if (!needsThis() && myMakeStatic) {
      aClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }
    PsiElement lastChild = aClass.getLastChild();
    if (lastChild instanceof PsiJavaToken && ((PsiJavaToken)lastChild).getTokenType() == JavaTokenType.SEMICOLON) {
      lastChild.delete();
    }

    return aClass;
  }

  private void appendClassInitializers(final PsiMethod constructor) throws IncorrectOperationException {
    final PsiClassInitializer[] initializers = myAnonClass.getInitializers();
    for (int i = 0; i < initializers.length; i++) {
      PsiClassInitializer initializer = initializers[i];
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiCodeBlock body = initializer.getBody();
        if (body != null) {
          final PsiJavaToken lBrace = body.getLBrace();
          final PsiJavaToken rBrace = body.getRBrace();
          constructor.getBody().addRange(lBrace.getNextSibling(), rBrace == null ? body.getLastChild() : rBrace.getPrevSibling());
        }
      }
    }
  }

  private static void copyClassBody(PsiClass sourceClass,
                                  PsiClass targetClass,
                                  boolean appendClassInitializersToConstructor) throws IncorrectOperationException {
    PsiElement lbrace = sourceClass.getLBrace();
    PsiElement rbrace = sourceClass.getRBrace();
    if (lbrace != null) {
      targetClass.addRange(lbrace.getNextSibling(), rbrace != null ? rbrace.getPrevSibling() : sourceClass.getLastChild());
      if (appendClassInitializersToConstructor) {  //see SCR 41692
        final PsiClassInitializer[] initializers = targetClass.getInitializers();
        for (int i = 0; i < initializers.length; i++) {
          PsiClassInitializer initializer = initializers[i];
          if (!initializer.hasModifierProperty(PsiModifier.STATIC)) initializer.delete();
        }
      }
    }
  }

  private void fillParameterList(PsiMethod constructor) throws IncorrectOperationException {
    PsiElementFactory factory = constructor.getManager().getElementFactory();
    PsiParameterList parameterList = constructor.getParameterList();
    for (int i = 0; i < myVariableInfos.length; i++) {
      VariableInfo info = myVariableInfos[i];
      if (info.passAsParameter) {
        PsiParameter parameter = factory.createParameter(info.parameterName, info.variable.getType());
        parameterList.add(parameter);
      }
    }
  }

  private void createFields(PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = myManager.getElementFactory();
    for (int i = 0; i < myVariableInfos.length; i++) {
      VariableInfo info = myVariableInfos[i];
      if (info.saveInField) {
        PsiField field = factory.createField(info.fieldName, info.variable.getType());
        field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        aClass.add(field);
      }
    }
  }

  private void createAssignmentStatements(PsiMethod constructor) throws IncorrectOperationException {
    PsiElementFactory factory = constructor.getManager().getElementFactory();
    for (int i = 0; i < myVariableInfos.length; i++) {
      VariableInfo info = myVariableInfos[i];
      if (info.saveInField) {
        String text = info.fieldName + "=a;";
        boolean useThis = info.passAsParameter && info.parameterName.equals(info.fieldName);
        if (useThis) {
          text = "this." + text;
        }
        PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(text, null);
        statement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(statement);
        // in order for "..." trick to work, the statement must be added to constructor first
        statement = (PsiExpressionStatement)constructor.getBody().add(statement);

        PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
        PsiReferenceExpression rExpr = (PsiReferenceExpression) assignment.getRExpression();
        PsiIdentifier identifier = (PsiIdentifier) rExpr.getReferenceNameElement();
        if (info.passAsParameter) {
          identifier.replace(factory.createIdentifier(info.parameterName));
        } else {
          TextRange range = rExpr.getTextRange();
          BlockSupport blockSupport = myProject.getComponent(BlockSupport.class);
          blockSupport.reparseRange(statement.getContainingFile(), range.getStartOffset(), range.getEndOffset(), "...");
        }
      }
    }
  }

  private void renameReferences(PsiElement scope) throws IncorrectOperationException {
    PsiSearchHelper helper = myManager.getSearchHelper();
    PsiElementFactory factory = myManager.getElementFactory();
    for (int i = 0; i < myVariableInfos.length; i++) {
      VariableInfo info = myVariableInfos[i];
      PsiReference[] references = helper.findReferences(info.variable, new LocalSearchScope(scope), false);
      if (references.length > 0) {
        for (int j = 0; j < references.length; j++) {
          PsiElement ref = references[j].getElement();
          PsiIdentifier identifier = (PsiIdentifier) ((PsiJavaCodeReferenceElement) ref).getReferenceNameElement();
          boolean renameToFieldName = !isUsedInInitializer(ref);
          PsiIdentifier newNameIdentifier = factory.createIdentifier(renameToFieldName? info.fieldName : info.parameterName);
          if (renameToFieldName) {
            identifier.replace(newNameIdentifier);
          } else {
            if (info.passAsParameter) {
              identifier.replace(newNameIdentifier);
            /*} else {
              PsiCodeBlock dummyBlock = factory.createCodeBlockFromText("{...}", null);
              PsiElement[] children = dummyBlock.getChildren();
              identifier.replace(children[1]);
              ref.addRange(children[2], children[children.length - 2]);*/
            }
          }
        }
      }
    }
  }

  private void createSuperStatement(PsiMethod constructor, PsiExpression[] paramExpressions) throws IncorrectOperationException {
    PsiCodeBlock body = constructor.getBody();
    final PsiElementFactory factory = constructor.getManager().getElementFactory();

    PsiStatement statement = factory.createStatementFromText("super();", null);
    statement = (PsiStatement) CodeStyleManager.getInstance(myProject).reformat(statement);
    statement = (PsiStatement) body.add(statement);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) ((PsiExpressionStatement) statement).getExpression();
    PsiExpressionList exprList = methodCall.getArgumentList();


    {
      final PsiThisExpression qualifiedThis =
        (PsiThisExpression) factory.createExpressionFromText("A.this", null);
      final PsiJavaCodeReferenceElement targetClassRef = factory.createClassReferenceElement(myTargetClass);
      qualifiedThis.getQualifier().replace(targetClassRef);

      for (int idx = 0; idx < paramExpressions.length; idx++) {
        PsiExpression expr = paramExpressions[idx];
        ChangeContextUtil.encodeContextInfo(expr, true);
        final PsiElement newExpr = exprList.add(expr);
        ChangeContextUtil.decodeContextInfo(newExpr, myTargetClass, qualifiedThis);
      }
    }

    class SupersConvertor extends PsiRecursiveElementVisitor {
      public void visitThisExpression(PsiThisExpression expression) {
        try {
          final PsiThisExpression qualifiedThis =
                  (PsiThisExpression) factory.createExpressionFromText("A.this", null);
          final PsiJavaCodeReferenceElement targetClassRef = factory.createClassReferenceElement(myTargetClass);
          qualifiedThis.getQualifier().replace(targetClassRef);
          expression.replace(qualifiedThis);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    }

    final SupersConvertor supersConvertor = new SupersConvertor();
    methodCall.getArgumentList().accept(supersConvertor);
  }


}
