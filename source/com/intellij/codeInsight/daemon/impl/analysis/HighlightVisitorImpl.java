package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.analysis.aspect.AspectHighlighter;
import com.intellij.codeInsight.daemon.impl.analysis.ejb.EjbHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.SetupJDKFix;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.jsp.JspElement;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

public class HighlightVisitorImpl extends PsiElementVisitor implements HighlightVisitor, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl");
  private final Project myProject;

  private final DaemonCodeAnalyzerSettings mySettings;
  private final PsiManager myManager;
  private PsiResolveHelper myResolveHelper;

  /**
   * @fabrique
   */
  protected HighlightInfoHolder myHolder;

  private RefCountHolder myRefCountHolder;

  private final JspHighlightVisitor myJspVisitor;
  private final XmlHighlightVisitor myXmlVisitor;
  private final JavadocHighlightVisitor myJavadocVisitor;
  private EjbHighlightVisitor myEjbHighlightVisitor;
  private Boolean runEjbHighlighting;
  private AspectHighlighter myAspectHighlightVisitor;
  // map codeBlock->List of PsiReferenceExpression of uninitailized final variables
  private final HashMap<PsiElement, List<PsiReferenceExpression>> myUninitializedVarProblems = new HashMap<PsiElement, List<PsiReferenceExpression>>();
  // map codeBlock->List of PsiReferenceExpression of extra initailization of final variable
  private final Map<PsiElement, List<PsiElement>> myFinalVarProblems = new THashMap<PsiElement, List<PsiElement>>();
  public static final String UNKNOWN_SYMBOL = "Cannot resolve symbol ''{0}''";

  private final Map<PsiMethod, PsiClass[]> myUnhandledExceptionsForMethod = new HashMap<PsiMethod, PsiClass[]>();
  private final Map<String, PsiClass> mySingleImportedClasses = new THashMap<String, PsiClass>();
  private final Map<String, PsiElement> mySingleImportedFields = new THashMap<String, PsiElement>();
  private final Map<MethodSignature, PsiElement> mySingleImportedMethods = new THashMap<MethodSignature, PsiElement>();

  public String getComponentName() {
    return "HighlightVisitorImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {}

  public void projectOpened() {}

  public void projectClosed() {}

  public HighlightVisitorImpl(Project project, DaemonCodeAnalyzerSettings settings, PsiManager manager) {
    myProject = project;
    mySettings = settings;
    myManager = manager;

    myJspVisitor = new JspHighlightVisitor();
    myXmlVisitor = new XmlHighlightVisitor();
    myJavadocVisitor = new JavadocHighlightVisitor(settings);

    myAspectHighlightVisitor = new AspectHighlighter();

    myResolveHelper = myManager.getResolveHelper();
  }

  public boolean suitableForFile(PsiFile file) {
    return true;
  }

  public void visit(PsiElement element, HighlightInfoHolder holder) {
    myHolder = holder;
    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(element.isValid());
    }
    element.accept(this);
    final PsiFile containingFile = element.getContainingFile();
    if (!(containingFile instanceof PsiCodeFragment) && containingFile instanceof PsiAspectFile) {
      element.accept(myAspectHighlightVisitor);
      myHolder.add(myAspectHighlightVisitor.getResults());
      myAspectHighlightVisitor.clearResults();
    }
    if (!myHolder.hasErrorResults()) {
      if (runEjbHighlighting == null) {
        runEjbHighlighting = Boolean.valueOf(EjbUtil.getEjbModuleProperties(element) != null);
      }
      if (runEjbHighlighting.booleanValue()) {
        if (myEjbHighlightVisitor == null) {
          myEjbHighlightVisitor = new EjbHighlightVisitor(myProject);
        }
        element.accept(myEjbHighlightVisitor);
        myHolder.addAll(myEjbHighlightVisitor.getResults());
        myEjbHighlightVisitor.clearResults();
      }
    }
  }

  public HighlightInfo[] getResults() {
    return myHolder.toArray(new HighlightInfo[myHolder.size()]);
  }

  public void init() {
    myUninitializedVarProblems.clear();
    myFinalVarProblems.clear();
    myUnhandledExceptionsForMethod.clear();
    mySingleImportedClasses.clear();
    mySingleImportedFields.clear();
    mySingleImportedMethods.clear();
  }

  public void setRefCountHolder(RefCountHolder refCountHolder) {
    myRefCountHolder = refCountHolder;
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    super.visitArrayInitializerExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitializerApplicable(expression));
  }

  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSillyAssignment(assignment));
    if (!myHolder.hasErrorResults()) visitExpression(assignment);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBinaryOperatorApplicable(expression));
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
    }
  }

  public void visitClass(PsiClass aClass) {
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkMissingPackageStatement(aClass));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
  }

  public void visitClassInitializer(PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
    }
  }

  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    super.visitClassObjectAccessExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
  }

  public void visitComment(PsiComment comment) {
    super.visitComment(comment);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findContinuedStatement()));
    }
  }

  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);
    if (!myHolder.hasErrorResults()
        && token.getTokenType() == JavaTokenType.RBRACE
        && token.getParent() instanceof PsiCodeBlock
        && token.getParent().getParent() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)token.getParent().getParent();
      myHolder.add(HighlightControlFlowUtil.checkMissingReturnStatement(method));
    }

  }

  public void visitJspElement(JspElement element) {
    element.accept(myJspVisitor);
    myHolder.addAll(myJspVisitor.getResult());
    myJspVisitor.clearResult();
  }

  public void visitDocComment(PsiDocComment comment) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkUnclosedComment(comment));
    if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) return;
    if (!myHolder.hasErrorResults()) {
      comment.accept(myJavadocVisitor);
      myHolder.add(myJavadocVisitor.getResult());
      myJavadocVisitor.clearResult();
    }
  }

  public void visitDocTag(PsiDocTag tag) {
    if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) return;
    tag.accept(myJavadocVisitor);
    myHolder.add(myJavadocVisitor.getResult());
    myJavadocVisitor.clearResult();
  }

  public void visitDocToken(PsiDocToken token) {
    if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) return;
    token.accept(myJavadocVisitor);
    myHolder.add(myJavadocVisitor.getResult());
    myJavadocVisitor.clearResult();
  }

  public void visitDocTagValue(PsiDocTagValue value) {
    if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) return;
    value.accept(myJavadocVisitor);
    myHolder.add(myJavadocVisitor.getResult());
    myJavadocVisitor.clearResult();
  }

  public void visitErrorElement(PsiErrorElement element) {
    HighlightInfoType errorType;
    if (PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null) {
      errorType = HighlightInfoType.JAVADOC_ERROR;
      if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.JAVADOC_ERROR)) return;
    }
    else {
      errorType = HighlightInfoType.ERROR;
    }

    TextRange range = element.getTextRange();
    if (range.getLength() > 0) {
      myHolder.add(HighlightInfo.createHighlightInfo(errorType, range, element.getErrorDescription()));
    }
    else {
      int offset = range.getStartOffset();
      PsiFile containingFile = element.getContainingFile();
      int fileLength = containingFile.getTextLength();
      PsiElement elementAtOffset = containingFile.findElementAt(offset);
      String text = elementAtOffset == null ? null : elementAtOffset.getText();
      if (offset < fileLength && text != null && !StringUtil.startsWithChar(text, '\n') && !StringUtil.startsWithChar(text, '\r')) {
        int start = offset;
        PsiElement prevElement = containingFile.findElementAt(offset - 1);
        if (offset > 0 && prevElement != null && prevElement.getText().equals("(") && StringUtil.startsWithChar(text, ')')) {
          start = offset - 1;
        }
        int end = offset + 1;
        final HighlightInfo info = HighlightInfo.createHighlightInfo(errorType, start, end, element.getErrorDescription());
        myHolder.add(info);
        info.navigationShift = offset - start;
      }
      else {
        int start;
        int end;
        if (offset > 0) {
          start = offset - 1;
          end = offset;
        }
        else {
          start = offset;
          end = offset < fileLength ? offset + 1 : offset;
        }
        final HighlightInfo info = HighlightInfo.createHighlightInfo(errorType, start, end, element.getErrorDescription());
        myHolder.add(info);
        info.isAfterEndOfLine = true;
      }
    }
  }

  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, mySettings));
    PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
    if (initializingClass != null) {
      initializingClass.accept(this);
    }
    if (!myHolder.hasErrorResults()) registerConstructorCall(enumConstant);
  }

  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    super.visitEnumConstantInitializer(enumConstantInitializer);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer));
  }

  public void visitExpression(PsiExpression expression) {
    if (myHolder.add(HighlightUtil.checkMustBeBoolean(expression))) return;
    if (expression instanceof PsiArrayAccessExpression
        && ((PsiArrayAccessExpression)expression).getArrayExpression() != null
        && ((PsiArrayAccessExpression)expression).getIndexExpression() != null) {
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(((PsiArrayAccessExpression)expression).getArrayExpression(),
                                                                 ((PsiArrayAccessExpression)expression).getIndexExpression()));
    }
    else if (expression.getParent() instanceof PsiNewExpression
             && ((PsiNewExpression)expression.getParent()).getQualifier() != expression
             && ((PsiNewExpression)expression.getParent()).getArrayInitializer() != expression) {
      // like in 'new String["s"]'
      myHolder.add(HighlightUtil.checkValidArrayAccessExpression(null, expression));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkVariableExpected(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkArrayInitalizerCompatibleTypes(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAssertOperatorTypes(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSynchronizedExpressionType(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkConstantExpressionOverflow(expression));
    if (!myHolder.hasErrorResults()
        && expression.getParent() instanceof PsiThrowStatement
        && ((PsiThrowStatement)expression.getParent()).getException() == expression) {
      final PsiType type = expression.getType();
      myHolder.add(HighlightUtil.checkMustBeThrowable(type, expression, true));
    }

    if (!myHolder.hasErrorResults()) {
      myHolder.add(AnnotationsHighlightUtil.checkConstantExpression(expression));
    }
  }

  public void visitField(PsiField field) {
    super.visitField(field);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    myHolder.add(GenericsHighlightUtil.checkForeachLoopParameterType(statement));
  }

  public void visitIdentifier(PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      myHolder.add(HighlightUtil.checkVariableAlreadyDefined((PsiVariable)parent));
    }
    else if (parent instanceof PsiClass) {
      myHolder.add(HighlightClassUtil.checkClassAlreadyImported((PsiClass)parent, identifier));
      myHolder.add(HighlightClassUtil.checkExternalizableHasPublicNoArgsConstructor((PsiClass)parent, identifier));
      if (!(parent instanceof PsiAnonymousClass) && !(parent instanceof PsiTypeParameter)) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)parent, ((PsiClass)parent).getNameIdentifier()));
      }
    }
    else if (parent instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)parent;
      if (method.isConstructor()) {
        myHolder.add(HighlightMethodUtil.checkConstructorName(method));
      }
    }
  }

  public void visitImportStatement(PsiImportStatement statement) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses));
  }


  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInstanceOfApplicable(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkInstanceOfGenericType(expression));
  }

  public void visitKeyword(PsiKeyword keyword) {
    super.visitKeyword(keyword);
    final PsiElement parent = keyword.getParent();
    if (parent instanceof PsiModifierList) {
      final PsiModifierList psiModifierList = (PsiModifierList)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAllowedModifier(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalModifierCombination(keyword, psiModifierList));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPublicClassInRightFile(keyword, psiModifierList));
      if (PsiModifier.ABSTRACT.equals(keyword.getText()) && psiModifierList.getParent() instanceof PsiMethod) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass((PsiMethod)psiModifierList.getParent(), keyword));
        }
      }
    }
    else if (keyword.getText().equals(PsiKeyword.CONTINUE) && parent instanceof PsiContinueStatement) {
      PsiContinueStatement statement = (PsiContinueStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkContinueOutsideLoop(statement));
    }
    else if (keyword.getText().equals(PsiKeyword.BREAK) && parent instanceof PsiBreakStatement) {
      PsiBreakStatement statement = (PsiBreakStatement)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkBreakOutsideLoop(statement));
    }
    else if (PsiKeyword.INTERFACE.equals(keyword.getText()) && parent instanceof PsiClass) {
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkInterfaceCannotBeLocal((PsiClass)parent));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalVoidType(keyword));

    if (PsiTreeUtil.getParentOfType(keyword, PsiDocTagValue.class) != null) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.JAVA_KEYWORD, keyword, null));
    }
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelWithoutStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkLabelAlreadyInUse(statement));
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);
    if (myHolder.hasErrorResults()) return;
    myHolder.add(HighlightUtil.checkLiteralExpressionParsingError(expression));
  }

  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodSameNameAsConstructor(method));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSameErasureSuperMethods(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkSameErasureMethods(method));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkOverrideAnnotation(method));
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
      myHolder.add(HighlightClassUtil.checkThingNotAllowedInInterface(method, method.getContainingClass()));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightNamesUtil.highlightMethodName(method, method.getNameIdentifier(), true));
  }

  private void highlightMethodOrClassName(PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiJavaCodeReferenceElement) {
      return;
    }
    if (parent instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      final PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null) {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false));
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(element));
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      final PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
      if (method == null) {
        PsiElement resolved = element.resolve();
        if (resolved instanceof PsiClass) {
          myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element));
        }
      }
      else {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, element, false));
      }
    }
    else if (parent instanceof PsiImportStatement && ((PsiImportStatement)parent).isOnDemand()) {
      // highlight on demand import as class
      myHolder.add(HighlightNamesUtil.highlightClassName(null, element));
    }
    else {
      final PsiElement resolved = element.resolve();
      if (resolved instanceof PsiClass) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element));
      }
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expr) {
    PsiExpressionList list = expr.getArgumentList();

    final PsiReferenceExpression methodExpression = expr.getMethodExpression();
    methodExpression.accept(this);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightMethodUtil.checkMethodCall(expr, list, myResolveHelper));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expr));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    final PsiElement parent = list.getParent();
    if (!myHolder.hasErrorResults() && parent instanceof PsiMethod) {
      myHolder.add(HighlightMethodUtil.checkMethodCanHaveBody((PsiMethod)parent));
    }
    if (parent instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)parent;
      final MethodSignatureBackedByPsiMethod methodSignature = new MethodSignatureBackedByPsiMethod(method, PsiSubstitutor.EMPTY);
      if (!method.isConstructor()) {
        final List<MethodSignatureBackedByPsiMethod> superMethodSignatures = PsiSuperMethodUtil.findSuperMethodSignaturesIncludingStatic(
          method, true);
        final List<MethodSignatureBackedByPsiMethod> superMethodCandidateSignatures = PsiSuperMethodUtil.findSuperMethodSignaturesIncludingStatic(
          method, false);
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkMethodWeakerPrivileges(methodSignature, superMethodCandidateSignatures, true));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, true));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(methodSignature, superMethodSignatures, true));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightMethodUtil.checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, mySettings));
        }
      }
      final PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkDuplicateMethod(aClass, method));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallBaseclassConstructor(method, myRefCountHolder));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkStaticMethodOverride(method));
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassMustBeAbstract(aClass));
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, myRefCountHolder));
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkInheritedMethodsWithSameSignature(aClass));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCyclicInheritance(aClass));
    }
    else if (parent instanceof PsiPointcutDef) {
      PsiPointcutDef pointcutDef = (PsiPointcutDef)parent;
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightAspectUtil.checkAbstractPointcutBody(pointcutDef));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightAspectUtil.checkPointcutOverridesFinal(pointcutDef));
    }
    else if (parent instanceof PsiEnumConstant) {
      if (!myHolder.hasErrorResults()) myHolder.addAll(GenericsHighlightUtil.checkEnumConstantModifierList(list));
    }

    if (!myHolder.hasErrorResults()) {
      HighlightInfo[] duplicateResults = AnnotationsHighlightUtil.checkDuplicatedAnnotations(list);
      for (int i = 0; i < duplicateResults.length; i++) {
        myHolder.add(duplicateResults[i]);
      }
    }
  }

  public void visitAnnotation(PsiAnnotation annotation) {
    myHolder.add(AnnotationsHighlightUtil.checkApplicability(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightNamesUtil.highlightAnnotationName(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
    if (!myHolder.hasErrorResults()) myHolder.add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
  }

  public void visitNewExpression(PsiNewExpression expression) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(expression, null));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkQualifiedNewOfStaticClass(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkNewExpression(expression, mySettings));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
    if (!myHolder.hasErrorResults()) registerConstructorCall(expression);

    if (!myHolder.hasErrorResults()) visitExpression(expression);
  }

  public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkPackageNameConformsToDirectoryName(statement));
  }

  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExceptionThrownInTry(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkCatchParameterIsClass(parameter));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    super.visitPostfixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    super.visitPrefixExpression(expression);
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
    }
  }

  public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement ref) {
    //check single-static-import statement
    String refName = ref.getReferenceName();
    final ResolveResult[] results = ref.multiResolve(false);

    if (results.length == 0) {
      String description = MessageFormat.format(UNKNOWN_SYMBOL, new Object[]{refName});
      final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getReferenceNameElement(), description);
      myHolder.add(info);
      QuickFixAction.registerQuickFixAction(info, SetupJDKFix.getInstnace());
    }
    else {
      PsiManager manager = ref.getManager();
      for (int i = 0; i < results.length; i++) {
        PsiElement element = results[i].getElement();
        if (!(element instanceof PsiModifierListOwner) || !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) continue;
        String importedString = null;
        if (element instanceof PsiClass) {
          PsiClass aClass = mySingleImportedClasses.get(refName);
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            importedString = "class '" + refName + "'";
          }
          mySingleImportedClasses.put(refName, (PsiClass)element);
        }
        else if (element instanceof PsiField) {
          PsiField field = (PsiField)mySingleImportedFields.get(refName);
          if (field != null && !manager.areElementsEquivalent(field, element)) {
            importedString = "field '" + refName + "'";
          }
          mySingleImportedFields.put(refName, element);
        }
        else if (element instanceof PsiMethod) {
          MethodSignature signature = ((PsiMethod)element).getSignature(PsiSubstitutor.EMPTY);
          PsiMethod method = (PsiMethod)mySingleImportedMethods.get(signature);
          if (method != null && !manager.areElementsEquivalent(method, element)) {
            importedString = "method '" + refName + "'";
          }
          mySingleImportedMethods.put(signature, element);
        }

        if (importedString != null) {
          String description = MessageFormat.format("{0} is already defined in a single-type import",
                                                    new Object[]{importedString});
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, description));
        }
      }
    }
  }

  private void registerConstructorCall(PsiConstructorCall constructorCall) {
    ResolveResult resolveResult = constructorCall.resolveMethodGenerics();
    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(constructorCall, resolveResult);
    }
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
    final ResolveResult result = ref.advancedResolve(true);
    final PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();
    if (myRefCountHolder != null) {
      myRefCountHolder.registerReference(ref, result);
    }

    myHolder.add(HighlightUtil.checkReference(ref, result, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkAbstractInstantiation(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExceptionAlreadyCaught(ref, resolved));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(HighlightUtil.checkDeprecated(resolved, ref.getReferenceNameElement(), DaemonCodeAnalyzerSettings.getInstance()));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkExceptionsNeverThrown(ref));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
    if (!myHolder.hasErrorResults()) {
      myHolder.add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, ref, result.getSubstitutor()));
    }

    PsiElement refParent = parent;
    if (resolved instanceof PsiClass && refParent instanceof PsiReferenceList) {
      myHolder.add(HighlightUtil.checkReferenceList(ref, (PsiReferenceList)refParent, result));
    }

    if (!myHolder.hasErrorResults()) {
      if (resolved instanceof PsiVariable) {
        myHolder.add(HighlightNamesUtil.highlightVariable((PsiVariable)resolved, ref.getReferenceNameElement()));
        myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref));
      }
      else {
        highlightMethodOrClassName(ref);
      }
    }
  }

  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType));
    }

    myHolder.add(AnnotationsHighlightUtil.checkValidAnnotationType(method.getReturnTypeElement()));
    myHolder.add(AnnotationsHighlightUtil.checkCyclicMemberType(method.getReturnTypeElement(), method.getContainingClass()));
  }

  public void visitNameValuePair(PsiNameValuePair pair) {
    myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair));
    if (!myHolder.hasErrorResults()) {
      PsiIdentifier nameId = pair.getNameIdentifier();
      if (nameId != null) myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ANNOTATION_ATTRIBUTE_NAME, nameId, null));
    }
  }

  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;
    if (initializer.getParent() instanceof PsiNameValuePair) {
      method = (PsiMethod)(initializer.getParent()).getReference().resolve();
    }
    else if (initializer.getParent() instanceof PsiAnnotationMethod) {
      method = (PsiMethod)initializer.getParent();
    }
    if (method != null) {
      PsiType type = method.getReturnType();
      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
        PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
        for (int i = 0; i < initializers.length; i++) {
          myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(initializers[i], type));
        }
      }
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
    if (!myHolder.hasErrorResults()) {
      visitExpression(expression);
    }
    final ResolveResult result = expression.advancedResolve(false);
    final PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      if (!myHolder.hasErrorResults()) {
        myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, resolved, myUninitializedVarProblems));
      }
      final PsiVariable variable = (PsiVariable)resolved;
      if (variable.hasModifierProperty(PsiModifier.FINAL) && !variable.hasInitializer()) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, myFinalVarProblems));
        }
        if (!myHolder.hasErrorResults()) myHolder.add(HighlightControlFlowUtil.checkFinalVariableInitalizedInLoop(expression, resolved));
      }
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkExpressionRequired(expression));
    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      myHolder.add(HighlightUtil.checkIllegalForwardReferenceToField(expression, (PsiField)resolved));
    }
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkConstructorCallMustBeFirstStatement(expression));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkAccessStaticMemberViaInstanceReference(expression, result));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
  }

  public void visitReferenceList(PsiReferenceList list) {
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      if (parent instanceof PsiAnnotationMethod) {
        PsiAnnotationMethod method = (PsiAnnotationMethod)parent;
        if (list == method.getThrowsList()) {
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, "@interface members may not have throws list"));
        }
      }
      else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
        if ("extends".equals(list.getFirstChild().getText())) {
          myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, "@interface may not have extends list"));
        }
      }
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkImplementsAllowed(list));
      if (!myHolder.hasErrorResults()) myHolder.add(HighlightClassUtil.checkClassExtendsOnlyOneClass(list));
    }
  }

  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkParametersOnRaw(list));
  }

  public void visitParameterList(PsiParameterList list) {
    if (list.getParent() instanceof PsiAnnotationMethod && list.getParameters().length > 0) {
      myHolder.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, "@interface members may not have parameters"));
    }
  }

  public void visitTypeParameterList(PsiTypeParameterList list) {
    myHolder.add(GenericsHighlightUtil.checkTypeParametersList(list));
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    myHolder.add(HighlightUtil.checkReturnStatementType(statement));
  }

  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkNotAStatement(statement));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkStatementPrependedWithCaseInsideSwitch(statement));
  }

  public void visitSuperExpression(PsiSuperExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightMethodUtil.checkAbstractMethodDirectCall(expr));
    if (!myHolder.hasErrorResults()) visitExpression(expr);
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkCaseStatement(statement));
  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkSwitchSelectorType(statement));
  }

  public void visitThisExpression(PsiThisExpression expr) {
    myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier()));
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr));
    if (!myHolder.hasErrorResults()) {
      visitExpression(expr);
    }
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    myHolder.add(HighlightUtil.checkUnhandledExceptions(statement, null));
    if (!myHolder.hasErrorResults()) visitStatement(statement);
  }

  public void visitTypeElement(PsiTypeElement type) {
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkIllegalType(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkClassUsedAsTypeParameter(type));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
  }

  public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    if (!myHolder.hasErrorResults()) myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
    if (!myHolder.hasErrorResults()) myHolder.add(GenericsHighlightUtil.checkUncheckedTypeCast(typeCast));
  }

  public void visitVariable(PsiVariable variable) {
    myHolder.add(HighlightUtil.checkVariableInitializerType(variable));

    myHolder.add(HighlightNamesUtil.highlightVariable(variable, variable.getNameIdentifier()));
  }

  public void visitXmlElement(final XmlElement element) {
    element.accept(myXmlVisitor);

    myHolder.addAll(myXmlVisitor.getResult());
    myXmlVisitor.clearResult();
  }
}
