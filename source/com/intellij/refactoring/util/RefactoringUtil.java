package com.intellij.refactoring.util;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.jsp.JspExpression;
import com.intellij.psi.search.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.InfoDialog;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;

import java.util.*;

public class RefactoringUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.RefactoringUtil");
  public static final int EXPR_COPY_SAFE = 0;
  public static final int EXPR_COPY_UNSAFE = 1;
  public static final int EXPR_COPY_PROHIBITED = 2;

  public static void showInfoDialog(String info, Project project) {
    RefactoringSettings settings = RefactoringSettings.getInstance();
    if (settings.IS_SHOW_ACTION_INFO) {
      InfoDialog usagesWarning = new InfoDialog(info, project);
      usagesWarning.show();
      settings.IS_SHOW_ACTION_INFO = usagesWarning.isToShowInFuture();
    }
  }

  public static boolean isSourceRoot(final PsiDirectory directory) {
    if (directory.getManager() == null) return false;
    final Project project = directory.getProject();
    if (project == null) return false;
    final VirtualFile virtualFile = directory.getVirtualFile();
    final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virtualFile);
    return Comparing.equal(virtualFile, sourceRootForFile);
  }

  public static boolean isInStaticContext(PsiElement element) {
    final PsiElement staticParentElement = HighlightUtil.getPossibleStaticParentElement(element, null);
    if (staticParentElement == null) return false;
    return staticParentElement instanceof PsiModifierListOwner
      && ((PsiModifierListOwner)staticParentElement).hasModifierProperty(PsiModifier.STATIC);
  }

  public static boolean isResolvableType(PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return Boolean.TRUE;
      }

      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(PsiClassType classType) {
        if (classType.resolve() == null) return Boolean.FALSE;
        PsiType[] parameters = classType.getParameters();
        for (int i = 0; i < parameters.length; i++) {
          PsiType parameter = parameters[i];
          if (parameter != null && !parameter.accept(this).booleanValue()) return Boolean.FALSE;
        }

        return Boolean.TRUE;
      }

      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        if (wildcardType.getBound() != null) return wildcardType.getBound().accept(this);
        return Boolean.TRUE;
      }
    }).booleanValue();
  }

  public static PsiElement replaceOccurenceWithFieldRef(PsiExpression occurrence, PsiField newField, PsiClass destinationClass)
    throws IncorrectOperationException {
    final PsiManager manager = occurrence.getManager();
    final String fieldName = newField.getName();
    final PsiVariable psiVariable = manager.getResolveHelper().resolveReferencedVariable(fieldName, occurrence);
    final PsiElementFactory factory = manager.getElementFactory();
    if (psiVariable != null && psiVariable.equals(newField)) {
      return occurrence.replace(factory.createExpressionFromText(fieldName, null));
    }
    else {
      final PsiReferenceExpression ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + fieldName, null);
      if (newField.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiReferenceExpression referenceExpression =
          factory.createReferenceExpression(destinationClass);
        ref.getQualifierExpression().replace(referenceExpression);
      }
      return occurrence.replace(ref);
    }
  }

  public static interface UsageInfoFactory {
    UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset);
  }

  public static void addUsagesInStringsAndComments(PsiElement element, String stringToSearch, List<UsageInfo> results,
                                                   UsageInfoFactory factory) {
    PsiManager manager = element.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    SearchScope scope = helper.getAccessScope(element);
    scope = scope.intersectWith(GlobalSearchScope.projectScope(manager.getProject()));
    int index = stringToSearch.lastIndexOf('.');
    String identifierToSearch = index >= 0 ? stringToSearch.substring(index + 1) : stringToSearch;

    PsiLiteralExpression[] literals = helper.findStringLiteralsContainingIdentifier(identifierToSearch, scope);
    for (int i = 0; i < literals.length; i++) {
      processStringOrComment(literals[i], stringToSearch, results, factory);
    }

    PsiElement[] comments = helper.findCommentsContainingIdentifier(identifierToSearch, scope);
    for (int i = 0; i < comments.length; i++) {
      processStringOrComment(comments[i], stringToSearch, results, factory);
    }
  }

  public static boolean isSearchInNonJavaEnabled(PsiElement element) {
    return element instanceof PsiPackage || element instanceof PsiDirectory
           || (element instanceof PsiClass && ((PsiClass)element).getQualifiedName() != null);
  }

  public static PsiElement getVariableScope(PsiLocalVariable localVar) {
    if (!(localVar instanceof ImplicitVariable)) {
      return localVar.getParent().getParent();
    }
    else {
      return ((ImplicitVariable)localVar).getDeclarationScope();
    }
  }

  public static void addUsagesInNonJavaFiles(PsiElement element, String stringToSearch, GlobalSearchScope searchScope,
                                             final List<UsageInfo> results, final UsageInfoFactory factory) {
    processUsagesInNonJavaFiles(element, stringToSearch, searchScope, new Processor<UsageInfo>() {
      public boolean process(UsageInfo t) {
        results.add(t);
        return true;
      }
    }, factory);
  }  

  public static void processUsagesInNonJavaFiles(PsiElement element, String stringToSearch, GlobalSearchScope searchScope,
                                             final Processor<UsageInfo> processor, final UsageInfoFactory factory) {
    PsiSearchHelper helper = element.getManager().getSearchHelper();

    helper.processUsagesInNonJavaFiles(stringToSearch,
                                       new PsiNonJavaFileReferenceProcessor() {
                                         public boolean process(PsiFile psiFile, int startOffset, int endOffset) {
                                           UsageInfo usageInfo = factory.createUsageInfo(psiFile, startOffset, endOffset);
                                           if (usageInfo != null) {
                                             if (!processor.process(usageInfo)) return false;
                                           }
                                           return true;
                                         }
                                       },
                                       searchScope);
  }

  private static void processStringOrComment(PsiElement element, String stringToSearch, List<UsageInfo> results,
                                             UsageInfoFactory factory) {
    String elementText = element.getText();
    for (int index = 0; index < elementText.length(); index++) {
      index = elementText.indexOf(stringToSearch, index);
      if (index < 0) break;

      if (index > 0) {
        char c = elementText.charAt(index - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      if (index + stringToSearch.length() < elementText.length()) {
        char c = elementText.charAt(index + stringToSearch.length());
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      UsageInfo usageInfo = factory.createUsageInfo(element, index, index + stringToSearch.length());
      if (usageInfo != null) {
        results.add(usageInfo);
      }

      index += stringToSearch.length();
    }
  }

  public static void renameNonCodeUsages(final Project project, final UsageInfo[] usages) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    HashMap<PsiFile,ArrayList<UsageOffset>> filesToOffsetsMap = new HashMap<PsiFile, ArrayList<UsageOffset>>();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      final PsiElement element = usage.getElement();

      if (element == null || !element.isValid()) continue;
      if (usage instanceof NonCodeUsageInfo) {
        final PsiFile containingFile = element.getContainingFile();
        int fileOffset = element.getTextRange().getStartOffset() + usage.startOffset;

        ArrayList<UsageOffset> list = filesToOffsetsMap.get(containingFile);
        if (list == null) {
          list = new ArrayList<UsageOffset>();
          filesToOffsetsMap.put(containingFile, list);
        }
        list.add(new UsageOffset(fileOffset, fileOffset + usage.endOffset - usage.startOffset,
                                 ((NonCodeUsageInfo)usage).newText));
      }
    }

    Iterator<PsiFile> iter = filesToOffsetsMap.keySet().iterator();
    while (iter.hasNext()) {
      PsiFile file = iter.next();
      final Document editorDocument = PsiDocumentManager.getInstance(project).getDocument(file);

      ArrayList<UsageOffset> list = filesToOffsetsMap.get(file);
      UsageOffset[] offsets = list.toArray(new UsageOffset[list.size()]);
      Arrays.sort(offsets);

      for (int i = offsets.length - 1; i >= 0; i--) {
        UsageOffset usageOffset = offsets[i];
        editorDocument.replaceString(usageOffset.startOffset, usageOffset.endOffset, usageOffset.newText);
      }
      PsiDocumentManager.getInstance(project).commitDocument(editorDocument);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }

  private static class UsageOffset implements Comparable {
    final int startOffset;
    final int endOffset;
    final String newText;

    public UsageOffset(int startOffset, int endOffset, String newText) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.newText = newText;
    }

    public int compareTo(Object o) {
      return startOffset - ((UsageOffset)o).startOffset;
    }
  }

  public static PsiReturnStatement[] findReturnStatements(PsiMethod method) {
    ArrayList<PsiElement> vector = new ArrayList<PsiElement>();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      addReturnStatements(vector, body);
    }
    return vector.toArray(new PsiReturnStatement[vector.size()]);
  }

  private static void addReturnStatements(ArrayList<PsiElement> vector, PsiElement element) {
    if (element instanceof PsiReturnStatement) {
      vector.add(element);
    }
    else if (element instanceof PsiClass) {
      return;
    }
    else {
      PsiElement[] children = element.getChildren();
      for (int i = 0; i < children.length; i++) {
        PsiElement child = children[i];
        addReturnStatements(vector, child);
      }
    }
  }


  public static PsiElement getParentStatement(PsiElement place, boolean skipScopingStatements) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiStatement) break;
      if (parent instanceof JspExpression) break;
      parent = parent.getParent();
      if (parent == null) return null;
    }
    PsiElement parentStatement = parent;
    parent = parentStatement instanceof PsiStatement ? parentStatement : parentStatement.getParent();
    while (parent instanceof PsiStatement) {
      if (!skipScopingStatements &&
          ((parent instanceof PsiForStatement && parentStatement == ((PsiForStatement)parent).getBody())
           || (parent instanceof PsiForeachStatement && parentStatement == ((PsiForeachStatement)parent).getBody())
           || (parent instanceof PsiWhileStatement && parentStatement == ((PsiWhileStatement)parent).getBody())
           || (parent instanceof PsiIfStatement &&
               (parentStatement == ((PsiIfStatement)parent).getThenBranch() || parentStatement == ((PsiIfStatement)parent).getElseBranch())))
      ) {
        return parentStatement;
      }
      parentStatement = parent;
      parent = parent.getParent();
    }
    return parentStatement;
  }


  public static PsiElement getParentExpressionAnchorElement(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (isExpressionAnchorElement(parent)) break;
      parent = parent.getParent();
      if (parent == null) return null;
    }
    PsiElement parentStatement = parent;
    parent = parentStatement.getParent();
    while (parent instanceof PsiStatement) {
      parentStatement = parent;
      parent = parent.getParent();
    }
    return parentStatement;
  }


  public static boolean isExpressionAnchorElement(PsiElement element) {
    return element instanceof PsiStatement || element instanceof JspExpression
           || element instanceof PsiClassInitializer
           || element instanceof PsiField || element instanceof PsiMethod;
  }

  /**
   * @param expression
   * @return loop body if expression is part of some loop's condition or for loop's increment part
   *         null otherwise
   */
  public static PsiElement getLoopForLoopCondition(PsiExpression expression) {
    PsiExpression outermost = expression;
    while (outermost.getParent() instanceof PsiExpression) {
      outermost = (PsiExpression)outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent();
      if (forStatement.getCondition() == outermost) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiExpressionStatement && outermost.getParent().getParent() instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)outermost.getParent().getParent();
      if (forStatement.getUpdate() == outermost.getParent()) {
        return forStatement;
      }
      else {
        return null;
      }
    }
    if (outermost.getParent() instanceof PsiWhileStatement) {
      return outermost.getParent();
    }
    if (outermost.getParent() instanceof PsiDoWhileStatement) {
      return outermost.getParent();
    }
    return null;
  }

  public static PsiClass getThisClass(PsiElement place) {
    PsiElement parent = place.getContext();
    PsiElement prev = null;
    while (true) {
      if (parent instanceof PsiClass) {
        if (!(parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getArgumentList() == prev))
          return (PsiClass)parent;
      }
      prev = parent;
      parent = parent.getContext();
      if (parent == null) return null;
    }
  }

  public static PsiClass getThisResolveClass(final PsiReferenceExpression place) {
    final ResolveResult resolveResult = place.advancedResolve(false);
    final PsiElement scope = resolveResult.getCurrentFileResolveScope();
    if (scope instanceof PsiClass)
      return (PsiClass) scope;
    return null;
    /*
    PsiElement parent = place.getContext();
    PsiElement prev = null;
    while (true) {
      if (parent instanceof PsiClass) {
        if (!(parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getArgumentList() == prev))
          return (PsiClass)parent;
      }
      prev = parent;
      parent = parent.getContext();
      if (parent == null) return null;
    }
    */
  }

  public static PsiCall getEnclosingConstructorCall (PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (ref instanceof PsiReferenceExpression && parent instanceof PsiMethodCallExpression) return (PsiCall)parent;

    if (parent instanceof PsiAnonymousClass) {
      parent = parent.getParent();
    }

    return parent instanceof PsiNewExpression ? (PsiNewExpression)parent : null;
  }

  public static void renameVariableReferences(PsiVariable variable, String newName, SearchScope scope)
    throws IncorrectOperationException {
    PsiManager manager = variable.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    PsiReference[] refs = helper.findReferences(variable, scope, false);
    for (int i = 0; i < refs.length; i++) {
      PsiReference reference = refs[i];
      if (reference != null) {
        reference.handleElementRename(newName);
      }
    }
  }

  public static boolean canBeDeclaredFinal(PsiVariable variable) {
    PsiManager manager = variable.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    PsiReference[] refs = helper.findReferences(variable, GlobalSearchScope.projectScope(manager.getProject()), false);
    for (int i = 0; i < refs.length; i++) {
      PsiReferenceExpression ref = (PsiReferenceExpression)refs[i].getElement();
      if (PsiUtil.isAccessedForWriting(ref)) {
        return false; // TODO: more correct check based on control flow
      }
    }
    return true;
  }

  public static PsiExpression inlineVariable(PsiLocalVariable variable, PsiExpression initializer,
                                             PsiJavaCodeReferenceElement ref) throws IncorrectOperationException {
    PsiManager manager = initializer.getManager();

    PsiClass variableParent = RefactoringUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringUtil.getThisClass(ref);
    initializer = convertInitializerToNormalExpression(initializer, variable.getType());

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)ref.replace(initializer);
    PsiType exprType = expr.getType();
    if (exprType != null && !variable.getType().isAssignableFrom(exprType)) { //can happen if initalizer's type was inferred using variable type
      PsiTypeCastExpression cast = (PsiTypeCastExpression)manager.getElementFactory().createExpressionFromText("(t)a", null);
      cast.getCastType().replace(variable.getTypeElement());
      cast.getOperand().replace(expr);
      expr = (PsiExpression)expr.replace(cast);
    }

    ChangeContextUtil.clearContextInfo(initializer);

    PsiClass thisClass = variableParent;
    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(variableParent, refParent)) {
      thisAccessExpr = createThisExpression(manager, null);
    }
    else {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = createThisExpression(manager, thisClass);
      }
    }
    return (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
  }

  public static PsiThisExpression createThisExpression(PsiManager manager, PsiClass qualifierClass)
    throws IncorrectOperationException {
    PsiElementFactory factory = manager.getElementFactory();
    if (qualifierClass != null) {
      PsiThisExpression qualifiedThis = (PsiThisExpression)factory.createExpressionFromText("q.this", null);
      qualifiedThis = (PsiThisExpression)CodeStyleManager.getInstance(manager.getProject()).reformat(qualifiedThis);
      qualifiedThis.getQualifier().bindToElement(qualifierClass);
      return qualifiedThis;
    }
    else {
      return (PsiThisExpression)factory.createExpressionFromText("this", null);
    }
  }

  /**
   * removes a reference to the specified class from the reference list given
   *
   * @return if removed  - a reference to the class or null if there were no references to this class in the reference list
   */
  public static PsiJavaCodeReferenceElement removeFromReferenceList(PsiReferenceList refList, PsiClass aClass)
    throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    if (refs != null) {
      for (int i = 0; i < refs.length; i++) {
        PsiJavaCodeReferenceElement ref = refs[i];
        if (aClass.equals(ref.resolve())) {
          PsiJavaCodeReferenceElement refCopy = (PsiJavaCodeReferenceElement)ref.copy();
          ref.delete();
          return refCopy;
        }
      }
    }
    return null;
  }

  public static PsiType getTypeByExpressionWithExpectedType(PsiExpression expr) {
    PsiType type = getTypeByExpression(expr);
    if (type != null) return type;
    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(expr.getProject()).getExpectedTypes(expr, false);
    if (expectedTypes.length == 1) {
      type = expectedTypes[0].getType();
      if (!type.equalsToText("java.lang.Object")) return type;
    }
    return null;
  }

  public static PsiType getTypeByExpression(PsiExpression expr) {
    PsiElementFactory factory = expr.getManager().getElementFactory();
    return getTypeByExpression(expr, factory);
  }

  private static PsiType getTypeByExpression(PsiExpression expr, final PsiElementFactory factory) {
    PsiType type = expr.getType();
    if (type == null) {
      if (expr instanceof PsiArrayInitializerExpression) {
        PsiExpression[] initializers = ((PsiArrayInitializerExpression)expr).getInitializers();
        if (initializers != null && initializers.length > 0) {
          PsiType initType = getTypeByExpression(initializers[0]);
          if (initType == null) return null;
          return initType.createArrayType();
        }
      }
      return null;
    }
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass instanceof PsiAnonymousClass) {
      type = ((PsiAnonymousClass)refClass).getBaseClassType();
    }
    if (type == PsiType.NULL) {
      ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(expr.getProject()).getExpectedTypes(expr, false);
      if (infos.length == 1) {
        type = infos[0].getType();
      }
      else {
        type = factory.createTypeByFQClassName("java.lang.Object", expr.getResolveScope());
      }
    }

    type = type.accept(new PsiTypeVisitor<PsiType>() {
        public PsiType visitArrayType(PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            PsiType type = componentType.accept(this);
            if (type == componentType) return arrayType;
            return type.createArrayType();
        }

        public PsiType visitType(PsiType type) {
            return type;
        }

        public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
            return capturedWildcardType.getWildcard();
        }

        public PsiType visitClassType(PsiClassType classType) {
            PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
            PsiClass aClass = resolveResult.getElement();
            if (aClass == null) return classType;
            boolean toExtend = false;
            Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            while(iterator.hasNext()) {
                PsiTypeParameter typeParameter = iterator.next();
                PsiType typeArgument = resolveResult.getSubstitutor().substitute(typeParameter);
                if (typeArgument instanceof PsiCapturedWildcardType) toExtend = true;
                substitutor = substitutor.put(typeParameter, typeArgument == null ? null : typeArgument.accept(this));
            }

            PsiType result = factory.createType(aClass, substitutor);
            if (toExtend) result = PsiWildcardType.createExtends(aClass.getManager(), result);
            return result;
        }
    });

    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiWildcardType) {
      componentType = ((PsiWildcardType)componentType).getExtendsBound();
      int dims = type.getArrayDimensions();
      for (int i = 0; i < dims; i++) componentType = componentType.createArrayType();
      return componentType;
    }

    return type;
  }

  public static boolean isAssignmentLHS(PsiElement element) {
    PsiElement parent = element.getParent();

    if (parent instanceof PsiAssignmentExpression
        && element.equals(((PsiAssignmentExpression)parent).getLExpression())) {
      return true;
    }
    else {
      return isPlusPlusOrMinusMinus(parent);
    }
  }

  public static boolean isPlusPlusOrMinusMinus(PsiElement element) {
    if (element instanceof PsiPrefixExpression) {
      PsiJavaToken operandSign = ((PsiPrefixExpression)element).getOperationSign();
      return operandSign.getTokenType() == JavaTokenType.PLUSPLUS
             || operandSign.getTokenType() == JavaTokenType.MINUSMINUS;
    }
    else if (element instanceof PsiPostfixExpression) {
      PsiJavaToken operandSign = ((PsiPostfixExpression)element).getOperationSign();
      return operandSign.getTokenType() == JavaTokenType.PLUSPLUS
             || operandSign.getTokenType() == JavaTokenType.MINUSMINUS;
    }
    else {
      return false;
    }
  }

  public static void removeFinalParameters(PsiMethod method)
    throws IncorrectOperationException {
    // Remove final parameters
    PsiParameterList paramList = method.getParameterList();
    if (paramList != null) {
      PsiParameter[] params = paramList.getParameters();

      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];

        if (param.hasModifierProperty(PsiModifier.FINAL)) {
          param.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
        }
      }
    }
  }

  public static PsiElement getAnchorElementForMultipleExpressions(PsiExpression[] occurrences, PsiElement scope) {
    PsiElement anchor = null;
    for (int i = 0; i < occurrences.length; i++) {
      PsiExpression occurrence = occurrences[i];
      if (scope != null && !PsiTreeUtil.isAncestor(scope, occurrence, false)) {
        continue;
      }
      PsiElement anchor1 = getParentExpressionAnchorElement(occurrence);
      if (anchor1 == null) {
        return null;
      }
      if (anchor == null) {
        anchor = anchor1;
      }
      else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(anchor, anchor1);
        PsiElement firstAnchor = anchor.getTextOffset() < anchor1.getTextOffset() ? anchor : anchor1;
        if (commonParent.equals(firstAnchor)) {
          anchor = firstAnchor;
        }
        else {
          PsiElement parent = firstAnchor;
          while (!parent.getParent().equals(commonParent)) {
            parent = parent.getParent();
          }
          final PsiElement newAnchor = getParentExpressionAnchorElement(parent);
          if (newAnchor != null) {
            anchor = newAnchor;
          }
          else {
            anchor = parent;
          }
        }
      }
    }

    if (occurrences.length > 1 && anchor.getParent().getParent() instanceof PsiSwitchStatement) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement)anchor.getParent().getParent();
      if (switchStatement.getBody().equals(anchor.getParent())) {
        int startOffset = occurrences[0].getTextRange().getStartOffset();
        int endOffset = occurrences[occurrences.length - 1].getTextRange().getEndOffset();
        PsiStatement[] statements = switchStatement.getBody().getStatements();
        boolean isInDifferentCases = false;
        for (int i = 0; i < statements.length; i++) {
          PsiStatement statement = statements[i];
          if (statement instanceof PsiSwitchLabelStatement) {
            int caseOffset = statement.getTextOffset();
            if (startOffset < caseOffset && caseOffset < endOffset) {
              isInDifferentCases = true;
              break;
            }
          }
        }
        if (isInDifferentCases) {
          anchor = switchStatement;
        }
      }
    }

    return anchor;
  }

  public static void setVisibility(PsiModifierList modifierList, String newVisibility)
    throws IncorrectOperationException {
    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
    modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
    modifierList.setModifierProperty(newVisibility, true);
  }

  public static boolean isMethodUsage(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCall) {
      return true;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return element.equals(((PsiAnonymousClass)parent).getBaseClassReference());
    }
    else {
      return false;
    }
  }

  public static PsiExpressionList getArgumentListByMethodReference(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).getArgumentList();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiNewExpression)parent.getParent()).getArgumentList();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public static PsiCallExpression getCallExpressionByMethodReference(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)parent;
    }
    else if (parent instanceof PsiNewExpression) {
      return (PsiNewExpression)parent;
    }
    else if (parent instanceof PsiAnonymousClass) {
      return (PsiNewExpression)parent.getParent();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  /**
   * @return List of highlighters
   */
  public static ArrayList highlightAllOccurences(Project project, PsiElement[] occurences, Editor editor) {
    ArrayList highlighters = new ArrayList();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, occurences, attributes, true, highlighters);
    return highlighters;
  }

  public static ArrayList highlightOccurences(Project project, PsiElement[] occurences, Editor editor) {
    if (occurences.length > 1) {
      return highlightAllOccurences(project, occurences, editor);
    }
    return new ArrayList();
  }

  public static String createTempVar(PsiExpression expr, PsiElement context, boolean declareFinal)
    throws IncorrectOperationException {
    PsiElement anchorStatement = getParentStatement(context, true);
    LOG.assertTrue(anchorStatement != null && anchorStatement.getParent() != null);

    Project project = expr.getProject();
    String[] suggestedNames =
      CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expr, null).names;
    final String prefix = suggestedNames[0];
    final String id = CodeStyleManager.getInstance(project).suggestUniqueVariableName(prefix, context, true);

    PsiElementFactory factory = expr.getManager().getElementFactory();

    if (expr instanceof PsiParenthesizedExpression) {
      PsiExpression expr1 = ((PsiParenthesizedExpression)expr).getExpression();
      if (expr1 != null) {
        expr = expr1;
      }
    }
    PsiDeclarationStatement decl =
      factory.createVariableDeclarationStatement(id, expr.getType(), expr);
    if (declareFinal) {
      ((PsiLocalVariable)decl.getDeclaredElements()[0]).getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    }
    anchorStatement.getParent().addBefore(decl, anchorStatement);

    return id;
  }

  public static int verifySafeCopyExpression(PsiElement expr) {
    return verifySafeCopyExpressionSubElement(expr);

  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof PsiThisExpression
        || element instanceof PsiSuperExpression
        || element instanceof PsiIdentifier
    ) {
      return EXPR_COPY_SAFE;
    }

    if (element instanceof PsiMethodCallExpression) {
      result = EXPR_COPY_UNSAFE;
    }

    if (element instanceof PsiNewExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof PsiAssignmentExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (isPlusPlusOrMinusMinus(element)) {
      return EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
  }

  public static PsiExpression convertInitializerToNormalExpression(PsiExpression expression,
                                                                   PsiType forcedReturnType)
    throws IncorrectOperationException {
    PsiExpression returnValue;
    if (expression instanceof PsiArrayInitializerExpression) {
      returnValue =
      createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)expression,
                                              forcedReturnType);
    }
    else {
      returnValue = expression;
    }
    return returnValue;
  }

  public static PsiExpression createNewExpressionFromArrayInitializer(PsiArrayInitializerExpression initializer,
                                                                      PsiType forcedType)
    throws IncorrectOperationException {
    PsiType initializerType = null;
    if (initializer != null) {
//        initializerType = myExpresssion.getType();
      if (forcedType != null) {
        initializerType = forcedType;
      }
      else {
        initializerType = getTypeByExpression(initializer);
      }
    }
    if (initializerType == null) {
      return initializer;
    }
    LOG.assertTrue(initializerType instanceof PsiArrayType);
    PsiElementFactory factory = initializer.getManager().getElementFactory();
    PsiNewExpression result =
      (PsiNewExpression)factory.createExpressionFromText("new " + initializerType.getPresentableText() + "{}", null);
    result = (PsiNewExpression)CodeStyleManager.getInstance(initializer.getProject()).reformat(result);
    result.getArrayInitializer().replace(initializer);
    return result;
  }

  public static void abstractizeMethod(PsiClass targetClass, PsiMethod method) throws IncorrectOperationException {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      body.delete();
    }

    method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
    method.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
    method.getModifierList().setModifierProperty(PsiModifier.SYNCHRONIZED, false);
    method.getModifierList().setModifierProperty(PsiModifier.NATIVE, false);

    if (!targetClass.isInterface()) {
      targetClass.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
    }

    removeFinalParameters(method);
  }

  public static boolean isInsideAnonymous(PsiElement element, PsiElement upTo) {
    for (PsiElement current = element;
         current != null && current != upTo;
         current = current.getParent()) {
      if (current instanceof PsiAnonymousClass) return true;
    }
    return false;
  }

  public static PsiExpression unparenthesizeExpression(PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression innerExpression = ((PsiParenthesizedExpression)expression).getExpression();
      if (innerExpression == null) return expression;
      expression = innerExpression;
    }
    return expression;
  }

  /**
   * @param expression
   * @return
   */
  public static PsiExpression outermostParenthesizedExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = (PsiParenthesizedExpression)expression.getParent();
    }
    return expression;
  }

  public static String getStringToSearch(PsiElement element, boolean nonJava) {
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      element = ((PsiDirectory)element).getPackage();
    }

    if (element instanceof PsiPackage) {
      return nonJava ? ((PsiPackage)element).getQualifiedName() : ((PsiPackage)element).getName();
    }
    else if (element instanceof PsiClass) {
      return nonJava ? ((PsiClass)element).getQualifiedName() : ((PsiClass)element).getName();
    }
    else if (element instanceof XmlTag) {
      return ((XmlTag)element).getValue().getTrimmedText();
    }
    else if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValue();
    }
    else if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    else {
      LOG.error("Unknown element type");
      return null;
    }
  }

  public static String getInnerClassNameForClassLoader(PsiClass aClass) {
    final String qName = aClass.getQualifiedName();
    return replaceDotsWithDollars(qName, aClass);

  }

  public static String replaceDotsWithDollars(final String qName, PsiClass aClass) {
    StringBuffer qNameBuffer = new StringBuffer(qName);

    int fromIndex = qNameBuffer.length();
    PsiElement parent = aClass.getParent();
    while (parent instanceof PsiClass) {
      final int dotIndex = qNameBuffer.lastIndexOf(".", fromIndex);
      if (dotIndex < 0) break;
      qNameBuffer.replace(dotIndex, dotIndex + 1, "$");
      fromIndex = dotIndex - 1;
      parent = parent.getParent();
    }
    return qNameBuffer.toString();
  }

  public static String getNewInnerClassName(PsiClass aClass, String oldInnerClassName, String newName) {
    if (!oldInnerClassName.endsWith(aClass.getName())) return newName;
    StringBuffer buffer = new StringBuffer(oldInnerClassName);
    buffer.replace(buffer.length() - aClass.getName().length(), buffer.length(), newName);
    return buffer.toString();
  }

  public static boolean isSuperOrThisCall(PsiStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof PsiExpressionStatement)) return false;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
    if (testForSuper) {
      if ("super".equals(methodExpression.getText())) return true;
    }
    if (testForThis) {
      if ("this".equals(methodExpression.getText())) return true;
    }

    return false;
  }

  public static void visitImplicitConstructorUsages(PsiClass aClass,
                                                    final ImplicitConstructorUsageVisitor implicitConstructorUsageVistor) {
    PsiManager manager = aClass.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    final PsiClass[] inheritors = manager.getSearchHelper().findInheritors(aClass, projectScope, false);

    for (int i = 0; i < inheritors.length; i++) {
      PsiClass inheritor = inheritors[i];

      visitImplicitSuperConstructorUsages(inheritor, implicitConstructorUsageVistor);
    }
  }

  public static void visitImplicitSuperConstructorUsages(PsiClass subClass,
                                                         final ImplicitConstructorUsageVisitor implicitConstructorUsageVistor) {
    final PsiMethod[] constructors = subClass.getConstructors();
    if (constructors.length > 0) {
      for (int j = 0; j < constructors.length; j++) {
        PsiMethod constructor = constructors[j];
        final PsiStatement[] statements = constructor.getBody().getStatements();
        if (statements.length < 1 || !isSuperOrThisCall(statements[0], true, true)) {
          implicitConstructorUsageVistor.visitConstructor(constructor);
        }
      }
    }
    else {
      implicitConstructorUsageVistor.visitClassWithoutConstructors(subClass);
//        visitImplicitConstructorUsages(inheritor, implicitConstructorUsageVistor);
    }
  }

  public static interface ImplicitConstructorUsageVisitor {
    void visitConstructor(PsiMethod constructor);

    void visitClassWithoutConstructors(PsiClass aClass);
  }

  public interface Graph<T> {
    Set<T> getVertices();

    Set<T> getTargets(T source);
  }

  /**
   * Returns subset of <code>graph.getVertices()</code> that is a tranistive closure (by <code>graph.getTargets()<code>)
   * of the following property: initialRelation.value() of vertex or <code>graph.getTargets(vertex)</code> is true.
   * <p/>
   * Note that <code>graph.getTargets()</code> is not neccesrily a subset of <code>graph.getVertex()</code>
   *
   * @param graph
   * @param initialRelation
   * @return subset of graph.getVertices()
   */
  public static <T> Set<T> transitiveClosure(Graph<T> graph, Condition<T> initialRelation) {
    Set<T> result = new HashSet<T>();

    final Set<T> vertices = graph.getVertices();
    boolean anyChanged;
    do {
      anyChanged = false;
      for (Iterator<T> iterator = vertices.iterator(); iterator.hasNext();) {
        T currentVertex = iterator.next();
        if (!result.contains(currentVertex)) {
          if (!initialRelation.value(currentVertex)) {
            Set<T> targets = graph.getTargets(currentVertex);
            for (Iterator<T> targetsIterator = targets.iterator(); targetsIterator.hasNext();) {
              T currentTarget = targetsIterator.next();
              if (result.contains(currentTarget) || initialRelation.value(currentTarget)) {
                result.add(currentVertex);
                anyChanged = true;
                break;
              }
            }
          }
          else {
            result.add(currentVertex);
          }
        }
      }
    }
    while (anyChanged);
    return result;
  }

  public static boolean equivalentTypes(PsiType t1, PsiType t2, PsiManager manager) {
    while (t1 instanceof PsiArrayType) {
      if (!(t2 instanceof PsiArrayType)) return false;
      t1 = ((PsiArrayType)t1).getComponentType();
      t2 = ((PsiArrayType)t2).getComponentType();
    }

    if (t1 instanceof PsiPrimitiveType) {
      if (t2 instanceof PsiPrimitiveType) {
        return t1.equals(t2);
      }
      else {
        return false;
      }
    }

    return manager.areElementsEquivalent(PsiUtil.resolveClassInType(t1), PsiUtil.resolveClassInType(t2));
  }

  public static List<PsiVariable> collectReferencedVariables(PsiElement scope) {
    final List<PsiVariable> result = new ArrayList<PsiVariable>();
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (element instanceof PsiVariable) {
          result.add((PsiVariable)element);
        }
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
    });
    return result;
  }

  public static boolean isModifiedInScope(PsiVariable variable, PsiElement scope) {
    final PsiReference[] references = variable.getManager().getSearchHelper().findReferences(variable, new LocalSearchScope(scope), false);
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      if (isAssignmentLHS(reference.getElement())) return true;
    }
    return false;
  }

  public static String getNameOfReferencedParameter(PsiDocTag tag) {
    LOG.assertTrue("param".equals(tag.getName()));
    final PsiElement[] dataElements = tag.getDataElements();
    if (dataElements.length < 1) return null;
    return dataElements[0].getText();
  }

  public static void fixJavadocsForParams(PsiMethod method, Set<PsiParameter> newParameters) throws IncorrectOperationException {
    final PsiDocComment docComment = method.getDocComment();
    if (docComment == null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiDocTag[] paramTags = docComment.findTagsByName("param");
    if (parameters.length > 0 && newParameters.size() < parameters.length && paramTags.length == 0) return;
    Map<PsiParameter, PsiDocTag> tagForParam = new HashMap<PsiParameter, PsiDocTag>();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      boolean found = false;
      for (int j = 0; j < paramTags.length; j++) {
        PsiDocTag paramTag = paramTags[j];
        if (parameter.getName().equals(getNameOfReferencedParameter(paramTag))) {
          tagForParam.put(parameter, paramTag);
          found = true;
          break;
        }
      }
      if (!found && !newParameters.contains(parameter)) {
        tagForParam.put(parameter, null);
      }
    }

    List<PsiDocTag> newTags = new ArrayList<PsiDocTag>();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (tagForParam.containsKey(parameter)) {
        final PsiDocTag psiDocTag = tagForParam.get(parameter);
        if (psiDocTag != null) {
          newTags.add((PsiDocTag)psiDocTag.copy());
        }
      }
      else {
        newTags.add(method.getManager().getElementFactory().createParamTag(parameter.getName(), ""));
      }
    }
    PsiDocTag anchor = paramTags.length > 0 ? paramTags[paramTags.length - 1] : null;
    for (Iterator<PsiDocTag> iterator = newTags.iterator(); iterator.hasNext();) {
      PsiDocTag psiDocTag = iterator.next();
      anchor = (PsiDocTag)docComment.addAfter(psiDocTag, anchor);
    }
    for (int i = 0; i < paramTags.length; i++) {
      PsiDocTag paramTag = paramTags[i];
      paramTag.delete();
    }
  }

  public static PsiDirectory createPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (VfsUtil.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (int i = 0; i < shortNames.length; i++) {
      String shortName = shortNames[i];
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        subdirectory = current.createSubdirectory(shortName);
      }
      current = subdirectory;
    }
    return current;
  }

  public static String qNameToCreateInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot)
    throws IncorrectOperationException {
    String targetQName = aPackage.getQualifiedName();
    String sourceRootPackage = ProjectRootManager.getInstance(aPackage.getManager().getProject()).getFileIndex().getPackageNameByDirectory(sourceRoot);
    if (sourceRootPackage == null || !targetQName.startsWith(sourceRootPackage)) {
      throw new IncorrectOperationException("Cannot create package '" + targetQName + "' in source folder " + sourceRoot.getPresentableUrl());
    }
    String temp = targetQName.substring(sourceRootPackage.length());
    if (StringUtil.startsWithChar(temp, '.')) temp = temp.substring(1);  // remove initial '.'
    String qNameToCreate = temp;
    return qNameToCreate;
  }


  public static PsiDirectory findPackageDirectoryInSourceRoot(PackageWrapper aPackage, final VirtualFile sourceRoot) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (VfsUtil.isAncestor(sourceRoot, directory.getVirtualFile(), false)) {
        return directory;
      }
    }
    String qNameToCreate;
    try {
      qNameToCreate = qNameToCreateInSourceRoot(aPackage, sourceRoot);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    final String[] shortNames = qNameToCreate.split("\\.");
    PsiDirectory current = aPackage.getManager().findDirectory(sourceRoot);
    LOG.assertTrue(current != null);
    for (int i = 0; i < shortNames.length; i++) {
      String shortName = shortNames[i];
      PsiDirectory subdirectory = current.findSubdirectory(shortName);
      if (subdirectory == null) {
        return null;
      }
      current = subdirectory;
    }
    return current;
  }

  public static String calculatePsiElementDescriptionList(PsiElement[] elements, StringBuffer buffer) {
    if (elements.length == 1) {
      buffer.append(UsageViewUtil.getType(elements[0]));
      buffer.append(' ');
      buffer.append(UsageViewUtil.getDescriptiveName(elements[0]));
    }
    else {
      Map<String, Ref<Integer>> map = new HashMap<String, Ref<Integer>>();
      for (int i = 0; i < elements.length; i++) {
        PsiElement element = elements[i];
        final String type = UsageViewUtil.getType(element);
        Ref<Integer> ref = map.get(type);
        if (ref == null) {
          ref = Ref.create(new Integer(0));
        }
        ref.set(new Integer(ref.get().intValue() + 1));
      }

  // !!! do not insert type parameters while JDK 1.4 still used as main DK for IDEA development
      Set entries = map.entrySet();
      int index = 0;
      for (Iterator iterator = entries.iterator(); iterator.hasNext(); index++) {
        Map.Entry entry = (Map.Entry) iterator.next();
        final String type = (String) entry.getKey();
        final int count = ((Ref<Integer>) entry.getValue()).get().intValue();
        if (index > 0 && index + 1 < entries.size()) {
          buffer.append(" ,");
        }
        else if (index > 0 && index == entries.size()) {
          buffer.append(" and ");
        }
        buffer.append(count);
        buffer.append(" ");
        buffer.append(count > 1 ? type : StringUtil.pluralize(type));
      }
    }
    return buffer.toString();
  }


  public static class ConditionCache <T> implements Condition<T> {
    private final Condition<T> myCondition;
    private final HashSet<T> myProcessedSet = new HashSet<T>();
    private final HashSet<T> myTrueSet = new HashSet<T>();

    public ConditionCache(Condition<T> condition) {
      myCondition = condition;
    }

    public boolean value(T object) {
      if (!myProcessedSet.contains(object)) {
        myProcessedSet.add(object);
        final boolean value = myCondition.value(object);
        if (value) {
          myTrueSet.add(object);
          return true;
        }
        return false;
      }
      return myTrueSet.contains(object);
    }
  }

  public static class IsInheritorOf implements Condition<PsiClass> {
    private final PsiClass myClass;
    private final ConditionCache<PsiClass> myConditionCache;

    public IsInheritorOf(PsiClass aClass) {
      myClass = aClass;
      myConditionCache = new ConditionCache<PsiClass>(new MyCondition());
    }

    public boolean value(PsiClass object) {
      return myConditionCache.value(object);
    }

    private class MyCondition implements Condition<PsiClass> {
      public boolean value(PsiClass aClass) {
        return aClass.isInheritor(myClass, true);
      }
    }
  }

  public static class IsDescendantOf implements Condition<PsiClass> {
    private final PsiClass myClass;
    private final ConditionCache<PsiClass> myConditionCache;

    public IsDescendantOf(PsiClass aClass) {
      myClass = aClass;
      myConditionCache = new ConditionCache<PsiClass>(new Condition<PsiClass>() {
        public boolean value(PsiClass aClass) {
          return InheritanceUtil.isInheritorOrSelf(aClass, myClass, true);
        }
      });
    }

    public boolean value(PsiClass aClass) {
      return myConditionCache.value(aClass);
    }
  }

  public static PsiElement[] getChilderenWithoutWhitespaceOrComments(PsiElement parent) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement current = parent.getFirstChild(); current != null; current = current.getNextSibling()) {
      if (!(current instanceof PsiWhiteSpace || current instanceof PsiComment)) {
        result.add(current);
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public static void processIncorrectOperation(final Project project, IncorrectOperationException e) {
    final String message = e.getMessage();
    final int index = message != null ? message.indexOf("java.io.IOException") : -1;
    if (index >= 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project, message.substring(index + "java.io.IOException".length()), "Error",
                                       Messages.getErrorIcon());
          }
        });
    }
    else {
      LOG.error(e);
    }
  }

  public static <T extends Collection<String>> T analyzeModuleConflicts(Project project,
                                                                        Collection<PsiElement> scope,
                                                                        PsiElement target,
                                                                        final T conflicts) {
    if (scope == null) return conflicts;
    final VirtualFile vFile;
    if (!(target instanceof PsiDirectory)) {
      vFile = target.getContainingFile().getVirtualFile();
    }
    else {
      vFile = ((PsiDirectory)target).getVirtualFile();
    }
    if (vFile == null) return conflicts;
    return analyzeModuleConflicts(project, scope, vFile, conflicts);
  }

  public static <T extends Collection<String>> T analyzeModuleConflicts(Project project,
                                                                        final Collection<PsiElement> scopes,
                                                                        final VirtualFile vFile,
                                                                        final T conflicts) {
    if (scopes == null) return conflicts;

    for (Iterator<PsiElement> iterator = scopes.iterator(); iterator.hasNext();) {
      final PsiElement scope = iterator.next();
      if (scope instanceof PsiPackage || scope instanceof PsiDirectory) return conflicts;
    }

    final Module targetModule = ModuleUtil.getModuleForFile(project, vFile);
    if (targetModule == null) return conflicts;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<PsiElement>();
    for (Iterator<PsiElement> iterator = scopes.iterator(); iterator.hasNext();) {
      final PsiElement scope = iterator.next();
      scope.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitReferenceElement(expression);
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          final PsiElement resolved = reference.resolve();
          if (resolved != null && !reported.contains(resolved)
              && !isAncestor(resolved, scopes)
              && !PsiSearchScopeUtil.isInScope(resolveScope, resolved)) {
            final String scopeDescription = ConflictsUtil.htmlEmphasize(ConflictsUtil.getDescription(ConflictsUtil.getContainer(reference), true));
            final String message =
              ConflictsUtil.capitalize(ConflictsUtil.htmlEmphasize(ConflictsUtil.getDescription(resolved, true))) +
              ", referenced in " + scopeDescription +
              ", will not be accessible in module " +
              ConflictsUtil.htmlEmphasize(targetModule.getName());
            conflicts.add(message);
            reported.add(resolved);
          }
        }
      });

    }
    return conflicts;
  }

  private static boolean isAncestor(final PsiElement resolved, final Collection<PsiElement> scopes) {
    for (Iterator<PsiElement> iterator = scopes.iterator(); iterator.hasNext();) {
      final PsiElement scope = iterator.next();
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true;
    }
    return false;
  }

}
