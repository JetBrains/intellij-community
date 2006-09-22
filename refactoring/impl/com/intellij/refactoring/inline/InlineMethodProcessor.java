package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineMethodProcessor");

  private PsiMethod myMethod;
  private PsiJavaCodeReferenceElement myReference;
  private Editor myEditor;
  private final boolean myInlineThisOnly;

  private PsiManager myManager;
  private PsiElementFactory myFactory;
  private CodeStyleManager myCodeStyleManager;

  private PsiBlockStatement[] myAddedBraces;
  private final String myDescriptiveName;
  private Map<PsiField, PsiClassInitializer> myAddedClassInitializers;
  private PsiMethod myMethodCopy;

  public InlineMethodProcessor(Project project,
                               PsiMethod method,
                               PsiJavaCodeReferenceElement reference,
                               Editor editor,
                               boolean isInlineThisOnly) {
    super(project);
    myMethod = method;
    myReference = reference;
    myEditor = editor;
    myInlineThisOnly = isInlineThisOnly;

    myManager = PsiManager.getInstance(myProject);
    myFactory = myManager.getElementFactory();
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    myDescriptiveName = UsageViewUtil.getDescriptiveName(myMethod);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.method.command", myDescriptiveName);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myReference)};
    final Collection<PsiReference> refCollection = ReferencesSearch.search(myMethod).findAll();
    Set<UsageInfo> usages = new HashSet<UsageInfo>();
    if (myReference != null) {
      usages.add(new UsageInfo(myReference));
    }
    for (PsiReference reference : refCollection) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myMethod = (PsiMethod)elements[0];
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    ArrayList<String> conflicts = new ArrayList<String>();

    if (!myInlineThisOnly) {
      final PsiMethod[] superMethods = myMethod.findSuperMethods();
      for (PsiMethod method : superMethods) {
        final String message = method.hasModifierProperty(PsiModifier.ABSTRACT)
                               ? RefactoringBundle
          .message("inlined.method.implements.method.from.0", method.getContainingClass().getQualifiedName())
                               : RefactoringBundle
                                 .message("inlined.method.overrides.method.from.0", method.getContainingClass().getQualifiedName());
        conflicts.add(message);
      }
    }

    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    myMethod.accept(collector);
    final Map<PsiMember, Set<PsiMember>> containersToReferenced = getInaccessible(collector.myReferencedMembers, usagesIn);

    final Set<PsiMember> containers = containersToReferenced.keySet();
    for (PsiMember container : containers) {
      Set<PsiMember> referencedInaccessible = containersToReferenced.get(container);
      for (PsiMember referenced : referencedInaccessible) {
        String message = RefactoringBundle.message("0.that.is.used.in.inlined.method.is.not.accessible.from.call.site.s.in.1",
                                                   ConflictsUtil.getDescription(referenced, true), ConflictsUtil.getDescription(container, true));
        conflicts.add(ConflictsUtil.capitalize(message));
      }
    }

    if (conflicts.size() > 0) {
      ConflictsDialog dialog = new ConflictsDialog(myProject, conflicts);
      dialog.show();
      if (!dialog.isOK()) {
        return false;
      }
    }

    if (!myInlineThisOnly) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myMethod)) return false;
    }

    prepareSuccessful();
    RefactoringUtil.sortDepthFirstRightLeftOrder(usagesIn);
    return true;
  }

  /**
   * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
   * to subsets of referencedElemens that are not accessible from that container
   *
   * @param referencedElements
   * @param usages
   */
  private static Map<PsiMember,Set<PsiMember>> getInaccessible(HashSet<PsiMember> referencedElements, UsageInfo[] usages) {
    Map<PsiMember,Set<PsiMember>> result = new HashMap<PsiMember, Set<PsiMember>>();

    for (UsageInfo usage : usages) {
      final PsiMember container = ConflictsUtil.getContainer(usage.getElement());
      Set<PsiMember> inaccessibleReferenced = result.get(container);
      if (inaccessibleReferenced == null) {
        inaccessibleReferenced = new HashSet<PsiMember>();
        result.put(container, inaccessibleReferenced);
        for (PsiMember member : referencedElements) {
          if (!PsiUtil.isAccessible(member, usage.getElement(), null)) {
            inaccessibleReferenced.add(member);
          }
        }
      }
    }

    return result;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    int col = -1;
    int line = -1;
    if (myEditor != null) {
      col = myEditor.getCaretModel().getLogicalPosition().column;
      line = myEditor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }

    final LvcsAction lvcsAction = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());
    try {
      doRefactoring(usages);
    }
    finally {
      LvcsIntegration.checkinFilesAfterRefactoring(myProject, lvcsAction);
    }

    if (myEditor != null) {
      LogicalPosition pos = new LogicalPosition(line, col);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }
  }

  private void doRefactoring(UsageInfo[] usages) {
    try {
      if (myInlineThisOnly) {
        if (myMethod.isConstructor()) {
          PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(myReference);
          if (constructorCall != null) {
            inlineConstructorCall(constructorCall);
          }
        }
        else {
          myReference = addBracesWhenNeeded(new PsiReferenceExpression[]{(PsiReferenceExpression)myReference})[0];
          inlineMethodCall((PsiReferenceExpression)myReference);
        }
      } else {
        if (myMethod.isConstructor()) {
          for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element instanceof PsiJavaCodeReferenceElement) {
              PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)element);
              if (constructorCall != null) {
                inlineConstructorCall(constructorCall);
              }
            }
          }
          myMethod.delete();
        } else {
          List<PsiReferenceExpression> refExprList = new ArrayList<PsiReferenceExpression>();
          for (final UsageInfo usage : usages) {
            final PsiElement element = usage.getElement();
            if (element instanceof PsiReferenceExpression) {
              refExprList.add((PsiReferenceExpression)element);
            }
          }
          PsiReferenceExpression[] refs = refExprList.toArray(
            new PsiReferenceExpression[refExprList.size()]);
          refs = addBracesWhenNeeded(refs);
          for (PsiReferenceExpression ref : refs) {
            inlineMethodCall(ref);
          }
          myMethod.delete();
        }
      }
      removeAddedBracesWhenPossible();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void inlineConstructorCall(PsiCall constructorCall) {
    final PsiMethod oldConstructor = constructorCall.resolveMethod();
    LOG.assertTrue (oldConstructor != null);
    final PsiManager manager = oldConstructor.getManager();
    final PsiExpression[] instanceCreationArguments = constructorCall.getArgumentList().getExpressions();
    final PsiParameter[] parameters = oldConstructor.getParameterList().getParameters();
    LOG.assertTrue (parameters.length == instanceCreationArguments.length);

    PsiStatement[] statements = oldConstructor.getBody().getStatements();
    LOG.assertTrue (statements.length == 1 && statements[0] instanceof PsiExpressionStatement);
    PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    LOG.assertTrue (expression instanceof PsiMethodCallExpression);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression.copy();
    final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      replaceParameterReferences(arg, oldConstructor, instanceCreationArguments);
    }

    try {
      constructorCall.getArgumentList().replace(methodCall.getArgumentList());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void replaceParameterReferences(final PsiElement element, 
                                                 final PsiMethod oldConstructor,
                                                 final PsiExpression[] instanceCreationArguments) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)element;
      PsiElement resolved = expression.resolve();
      if (resolved instanceof PsiParameter && element.getManager().areElementsEquivalent(((PsiParameter)resolved).getDeclarationScope(),
                                                                            oldConstructor)) {
        PsiElement declarationScope = ((PsiParameter)resolved).getDeclarationScope();
        PsiParameter[] declarationParameters = ((PsiMethod)declarationScope).getParameterList().getParameters();
        for (int j = 0; j < declarationParameters.length; j++) {
          if (declarationParameters[j] == resolved) {
            try {
              expression.replace(instanceCreationArguments[j]);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    } else {
      PsiElement child = element.getFirstChild();
      while (child != null) {
        PsiElement next = child.getNextSibling();
        replaceParameterReferences(child, oldConstructor, instanceCreationArguments);
        child = next;
      }
    }
  }

  private void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
    ChangeContextUtil.encodeContextInfo(myMethod, false);
    myMethodCopy = (PsiMethod)myMethod.copy();
    ChangeContextUtil.clearContextInfo(myMethod);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    PsiSubstitutor callSubstitutor = getCallSubstitutor(methodCall);
    BlockData blockData = prepareBlock(ref, callSubstitutor);
    solveVariableNameConflicts(blockData.block, ref);
    if (callSubstitutor != PsiSubstitutor.EMPTY) {
      substituteMethodTypeParams(blockData.block, callSubstitutor);
    }
    addParmAndThisVarInitializers(blockData, methodCall);

    PsiElement anchor = RefactoringUtil.getParentStatement(methodCall, true);
    if (anchor == null) return;
    PsiElement anchorParent = anchor.getParent();
    PsiLocalVariable thisVar = null;
    PsiLocalVariable[] parmVars = new PsiLocalVariable[blockData.parmVars.length];
    PsiLocalVariable resultVar = null;
    PsiStatement[] statements = blockData.block.getStatements();
    if (statements.length > 0) {
      int last = statements.length - 1;
      /*PsiElement first = statements[0];
      PsiElement last = statements[statements.length - 1];*/

      if (statements.length > 0 && statements[statements.length - 1]instanceof PsiReturnStatement) {
        last--;
      }

      int first = 0;
      if (first <= last) {
        PsiElement firstAdded = anchorParent.addRangeBefore(statements[first], statements[last], anchor);

        PsiElement current = firstAdded.getPrevSibling();
        if (blockData.thisVar != null) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          thisVar = (PsiLocalVariable)statement.getDeclaredElements()[0];
          current = statement;
        }
        for (int i = 0; i < parmVars.length; i++) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          parmVars[i] = (PsiLocalVariable)statement.getDeclaredElements()[0];
          current = statement;
        }
        if (blockData.resultVar != null) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          resultVar = (PsiLocalVariable)statement.getDeclaredElements()[0];
        }
      }
      if (statements.length > 0) {
        final PsiStatement lastStatement = statements[statements.length - 1];
        if (lastStatement instanceof PsiReturnStatement) {
          final PsiExpression returnValue = ((PsiReturnStatement)lastStatement).getReturnValue();
          while (returnValue instanceof PsiReferenceExpression) ((PsiReferenceExpression)returnValue).getQualifierExpression();
          if (returnValue != null && PsiUtil.isStatement(returnValue)) {
            PsiExpressionStatement exprStatement = (PsiExpressionStatement)myFactory.createStatementFromText("a;", null);
            exprStatement.getExpression().replace(returnValue);
            anchorParent.addBefore(exprStatement, anchor);
          }
        }
      }
    }

    if (methodCall.getParent()instanceof PsiExpressionStatement) {
      methodCall.getParent().delete();
    }
    else {
      if (blockData.resultVar != null) {
        PsiExpression expr = myFactory.createExpressionFromText(blockData.resultVar.getName(), null);
        methodCall.replace(expr);
      }
      else {
        //??
      }
    }

    PsiClass thisClass = myMethod.getContainingClass();
    PsiExpression thisAccessExpr;
    if (thisVar != null) {
      if (!canInlineParmOrThisVariable(thisVar, false)) {
        thisAccessExpr = myFactory.createExpressionFromText(thisVar.getName(), null);
      } else {
        thisAccessExpr = thisVar.getInitializer();
      }
    }
    else {
      thisAccessExpr = null;
    }
    ChangeContextUtil.decodeContextInfo(anchorParent, thisClass, thisAccessExpr);

    if (thisVar != null) {
      inlineParmOrThisVariable(thisVar, false);
    }
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    for (int i = 0; i < parmVars.length; i++) {
      final boolean strictlyFinal;
      final PsiParameter parameter = parameters[i];
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        strictlyFinal = isStrictlyFinal(parameter);
      }
      else {
        strictlyFinal = false;
      }
      inlineParmOrThisVariable(parmVars[i], strictlyFinal);
    }
    if (resultVar != null) {
      inlineResultVariable(resultVar);
    }

    ChangeContextUtil.clearContextInfo(anchorParent);
  }

  private PsiSubstitutor getCallSubstitutor (PsiMethodCallExpression methodCall) {
    JavaResolveResult resolveResult = methodCall.getMethodExpression().advancedResolve(false);
    LOG.assertTrue (myManager.areElementsEquivalent(resolveResult.getElement(), myMethod));
    if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
      Iterator<PsiTypeParameter> oldTypeParameters = PsiUtil.typeParametersIterator(myMethod);
      Iterator<PsiTypeParameter> newTypeParameters = PsiUtil.typeParametersIterator(myMethodCopy);
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      while (newTypeParameters.hasNext()) {
        final PsiTypeParameter newTypeParameter = newTypeParameters.next();
        final PsiTypeParameter oldTypeParameter = oldTypeParameters.next();
        substitutor = substitutor.put(newTypeParameter, resolveResult.getSubstitutor().substitute(oldTypeParameter));
      }
      return substitutor;
    }

    return PsiSubstitutor.EMPTY;
  }

  private void substituteMethodTypeParams(PsiElement scope, final PsiSubstitutor substitutor) {
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitTypeElement(PsiTypeElement typeElement) {
        PsiType type = typeElement.getType();

        if (type instanceof PsiClassType) {
          JavaResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          PsiElement resolved = resolveResult.getElement();
          if (resolved instanceof PsiTypeParameter && ((PsiTypeParameter)resolved).getOwner() == myMethodCopy) {
            PsiType newType = resolveResult.getSubstitutor().putAll(substitutor).substitute((PsiTypeParameter)resolved);
            try {
              typeElement.replace(myFactory.createTypeElement(newType));
              return;
            }
            catch (IncorrectOperationException e) { LOG.error(e); }
          }
        }
        super.visitTypeElement(typeElement);
      }
    });
  }

  private boolean isStrictlyFinal(PsiParameter parameter) {
    PsiSearchHelper searchHelper = myManager.getSearchHelper();
    final PsiReference[] references = searchHelper.findReferences(parameter,
                                                                  GlobalSearchScope.projectScope(myProject),
                                                                  false);
    for (PsiReference reference : references) {
      final PsiElement refElement = reference.getElement();
      final PsiElement anonymousClass = PsiTreeUtil.getParentOfType(refElement, PsiAnonymousClass.class);
      if (anonymousClass != null && PsiTreeUtil.isAncestor(myMethod, anonymousClass, true)) {
        return true;
      }
    }
    return false;
  }


  private boolean syncNeeded(final PsiReferenceExpression ref) {
    if (!myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    final PsiMethod containingMethod = Util.getContainingMethod(ref);
    if (containingMethod == null) return true;
    if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return true;
    final PsiClass sourceContainingClass = myMethod.getContainingClass();
    final PsiClass targetContainingClass = containingMethod.getContainingClass();
    if (sourceContainingClass.equals (targetContainingClass)) return false;
    return true;
  }

  private BlockData prepareBlock(PsiReferenceExpression ref, final PsiSubstitutor callSubstitutor) throws IncorrectOperationException {
    final PsiCodeBlock block = myMethodCopy.getBody();
    final PsiStatement[] originalStatements = block.getStatements();

    PsiLocalVariable resultVar = null;
    PsiType returnType = callSubstitutor.substitute(myMethod.getReturnType());
    String resultName = null;
    if (returnType != null && returnType != PsiType.VOID) {
      resultName = myCodeStyleManager.propertyNameToVariableName("result", VariableKind.LOCAL_VARIABLE);
      resultName = myCodeStyleManager.suggestUniqueVariableName(resultName, block.getFirstChild(), true);
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(resultName, returnType, null);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      resultVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
    }

    PsiParameter[] parms = myMethodCopy.getParameterList().getParameters();
    PsiLocalVariable[] parmVars = new PsiLocalVariable[parms.length];
    for (int i = parms.length - 1; i >= 0; i--) {
      PsiParameter parm = parms[i];
      String parmName = parm.getName();
      String name = parmName;
      name = myCodeStyleManager.variableNameToPropertyName(name, VariableKind.PARAMETER);
      name = myCodeStyleManager.propertyNameToVariableName(name, VariableKind.LOCAL_VARIABLE);
      if (!name.equals(parmName)) {
        name = myCodeStyleManager.suggestUniqueVariableName(name, block.getFirstChild(), true);
      }
      RefactoringUtil.renameVariableReferences(parm, name, GlobalSearchScope.projectScope(myProject));
      PsiType paramType = parm.getType();
      @NonNls String defaultValue;
      if (paramType instanceof PsiEllipsisType) {
        final PsiEllipsisType ellipsisType = (PsiEllipsisType)paramType;
        paramType = ellipsisType.toArrayType();
        defaultValue = "new " + ellipsisType.getComponentType().getCanonicalText() + "[]{}";
      } else {
        defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
      }

      PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(name, callSubstitutor.substitute(paramType),
                                                                                         initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parmVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      parmVars[i].getModifierList().setModifierProperty(PsiModifier.FINAL, parm.hasModifierProperty(PsiModifier.FINAL));
    }

    PsiLocalVariable thisVar = null;
    if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = myMethod.getContainingClass();

      if (containingClass != null) {
        PsiType thisType = myFactory.createType(containingClass, callSubstitutor);
        String[] names = myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, thisType)
          .names;
        String thisVarName = names[0];
        thisVarName = myCodeStyleManager.suggestUniqueVariableName(thisVarName, block.getFirstChild(), true);
        PsiExpression initializer = myFactory.createExpressionFromText("null", null);
        PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(thisVarName, thisType,
                                                                                           initializer);
        declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
        thisVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      }
    }

    if (thisVar != null && syncNeeded (ref)) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)myFactory.createStatementFromText("synchronized(" + thisVar.getName() + "){}", block);
      synchronizedStatement =
      (PsiSynchronizedStatement)CodeStyleManager.getInstance(myProject).reformat(synchronizedStatement);
      synchronizedStatement = (PsiSynchronizedStatement)block.add(synchronizedStatement);
      final PsiCodeBlock synchronizedBody = synchronizedStatement.getBody();
      for (final PsiStatement originalStatement : originalStatements) {
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
    }

    if (resultName != null) {
      PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(myMethodCopy);
      for (PsiReturnStatement returnStatement : returnStatements) {
        if (returnStatement.getReturnValue() == null) continue;
        PsiStatement statement = myFactory.createStatementFromText(resultName + "=0;", null);
        statement = (PsiStatement)myCodeStyleManager.reformat(statement);
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)((PsiExpressionStatement)statement).getExpression();
        assignment.getRExpression().replace(returnStatement.getReturnValue());
        returnStatement.replace(statement);
      }
    }

    return new BlockData(block, thisVar, parmVars, resultVar);
  }

  private void solveVariableNameConflicts(PsiElement scope, final PsiElement placeToInsert)
    throws IncorrectOperationException {
    if (scope instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)scope;
      String name = var.getName();
      String oldName = name;
      while (true) {
        String newName = myCodeStyleManager.suggestUniqueVariableName(name, placeToInsert, true);
        if (newName.equals(name)) break;
        name = newName;
        newName = myCodeStyleManager.suggestUniqueVariableName(name, var, true);
        if (newName.equals(name)) break;
        name = newName;
      }
      if (!name.equals(oldName)) {
        RefactoringUtil.renameVariableReferences(var, name, GlobalSearchScope.projectScope(myProject));
        var.getNameIdentifier().replace(myFactory.createIdentifier(name));
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      solveVariableNameConflicts(child, placeToInsert);
    }
  }

  private void addParmAndThisVarInitializers(BlockData blockData, PsiMethodCallExpression methodCall)
    throws IncorrectOperationException {
    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for (int i = 0; i < args.length; i++) {
      int j = Math.min(i, blockData.parmVars.length - 1);
      final PsiExpression initializer = blockData.parmVars[j].getInitializer();
      LOG.assertTrue(initializer != null);
      if (initializer instanceof PsiNewExpression && ((PsiNewExpression)initializer).getArrayInitializer() != null) { //varargs initializer
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        arrayInitializer.add(args[i]);
        continue;
      }

      initializer.replace(args[i]);
    }

    if (blockData.thisVar != null) {
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (qualifier == null) {
        PsiElement parent = methodCall.getParent();
        while (true) {
          if (parent instanceof PsiClass) break;
          if (parent instanceof PsiFile) break;
          parent = parent.getParent();
        }
        if (parent instanceof PsiClass) {
          final PsiClass parentClass = (PsiClass) parent;
          final PsiClass containingClass = myMethod.getContainingClass();
          if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
            qualifier = myFactory.createExpressionFromText("this", null);
          }
          else {
            String name = containingClass.getName();
            if (name != null) {
              qualifier = myFactory.createExpressionFromText(name + ".this", null);
            }
            else { //?
              qualifier = myFactory.createExpressionFromText("this", null);
            }
          }
        }
        else {
          qualifier = myFactory.createExpressionFromText("this", null);
        }
      } else if (qualifier instanceof PsiSuperExpression) {
        qualifier = myFactory.createExpressionFromText("this", null);
      }
      blockData.thisVar.getInitializer().replace(qualifier);
    }
  }

  private boolean canInlineParmOrThisVariable(PsiLocalVariable variable, boolean strictlyFinal) {
    boolean isAccessedForWriting = false;
    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (refElement instanceof PsiExpression) {
        if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          isAccessedForWriting = true;
        }
      }
    }

    PsiExpression initializer = variable.getInitializer();
    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    return canInlineParmOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting);
  }

  private void inlineParmOrThisVariable(PsiLocalVariable variable, boolean strictlyFinal)
    throws IncorrectOperationException {
    PsiReference firstRef = ReferencesSearch.search(variable).findFirst();

    if (firstRef == null) {
      variable.getParent().delete(); //Q: side effects?
      return;
    }


    boolean isAccessedForWriting = false;
    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (refElement instanceof PsiExpression) {
        if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          isAccessedForWriting = true;
        }
      }
    }

    PsiExpression initializer = variable.getInitializer();
    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    if (canInlineParmOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting)) {
      if (shouldBeFinal) {
        declareUsedLocalsFinal(initializer, strictlyFinal);
      }
      for (PsiReference ref : refs) {
        final PsiJavaCodeReferenceElement javaRef = (PsiJavaCodeReferenceElement)ref;
        if (initializer instanceof PsiThisExpression && ((PsiThisExpression)initializer).getQualifier() == null) {
          final PsiClass varThisClass = RefactoringUtil.getThisClass(variable);
          if (RefactoringUtil.getThisClass(javaRef) != varThisClass) {
            initializer = myManager.getElementFactory().createExpressionFromText(varThisClass.getName() + ".this", variable);
          }
        }

        PsiExpression expr = RefactoringUtil.inlineVariable(variable, initializer,
                                                            javaRef);

        //Q: move the following code to some util? (addition to inline?)
        if (expr instanceof PsiThisExpression) {
          if (expr.getParent() instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExpr = (PsiReferenceExpression)expr.getParent();
            PsiElement refElement = refExpr.resolve();
            PsiExpression exprCopy = (PsiExpression)refExpr.copy();
            refExpr =
            (PsiReferenceExpression)refExpr.replace(
              myFactory.createExpressionFromText(refExpr.getReferenceName(), null));
            if (refElement != null) {
              PsiElement newRefElement = refExpr.resolve();
              if (!refElement.equals(newRefElement)) {
                // change back
                refExpr.replace(exprCopy);
              }
            }
          }
        }
      }
      variable.getParent().delete();
    }
  }

  private boolean canInlineParmOrThisVariable(PsiExpression initializer,
                                              boolean shouldBeFinal,
                                              boolean strictlyFinal,
                                              int accessCount,
                                              boolean isAccessedForWriting) {
    if (strictlyFinal) {
      class CanAllLocalsBeDeclaredFinal extends PsiRecursiveElementVisitor {
        boolean success = true;

        public void visitReferenceExpression(PsiReferenceExpression expression) {
          final PsiElement psiElement = expression.resolve();
          if (psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) {
            if (!RefactoringUtil.canBeDeclaredFinal((PsiVariable)psiElement)) {
              success = false;
            }
          }
        }

        public void visitElement(PsiElement element) {
          if (success) {
            super.visitElement(element);
          }
        }
      }

      final CanAllLocalsBeDeclaredFinal canAllLocalsBeDeclaredFinal = new CanAllLocalsBeDeclaredFinal();
      initializer.accept(canAllLocalsBeDeclaredFinal);
      if (!canAllLocalsBeDeclaredFinal.success) return false;
    }
    if (initializer instanceof PsiReferenceExpression) {
      PsiVariable refVar = (PsiVariable)((PsiReferenceExpression)initializer).resolve();
      if (refVar == null) {
        return !isAccessedForWriting;
      }
      if (refVar instanceof PsiField) {
        if (isAccessedForWriting) return false;
        /*
        PsiField field = (PsiField)refVar;
        if (isFieldNonModifiable(field)){
          return true;
        }
        //TODO: other cases
        return false;
        */
        return true; //TODO: "suspicous" places to review by user!
      }
      else {
        if (isAccessedForWriting) {
          if (refVar.hasModifierProperty(PsiModifier.FINAL) || shouldBeFinal) return false;
          PsiReference[] refs = myManager.getSearchHelper().findReferences(refVar,
                                                                           GlobalSearchScope.projectScope(
                                                                           myProject),
                                                                           false);
          return refs.length == 1; //TODO: control flow
        }
        else {
          if (shouldBeFinal) {
            if (refVar.hasModifierProperty(PsiModifier.FINAL)) return true;
            return RefactoringUtil.canBeDeclaredFinal(refVar);
          }
          return true;
        }
      }
    }
    else if (isAccessedForWriting) {
      return false;
    }
    else if (initializer instanceof PsiCallExpression) {
      if (accessCount > 1) return false;
      final PsiExpressionList argumentList = ((PsiCallExpression)initializer).getArgumentList();
      if (argumentList == null) return false;
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (!canInlineParmOrThisVariable(expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
          return false;
        }
      }
      return true; //TODO: "suspicous" places to review by user!
    }
    else if (initializer instanceof PsiLiteralExpression) {
      return true;
    }
    else if (initializer instanceof PsiArrayAccessExpression) {
      final PsiExpression arrayExpression = ((PsiArrayAccessExpression)initializer).getArrayExpression();
      final PsiExpression indexExpression = ((PsiArrayAccessExpression)initializer).getIndexExpression();
      return canInlineParmOrThisVariable(arrayExpression, shouldBeFinal, strictlyFinal, accessCount, false)
             && canInlineParmOrThisVariable(indexExpression, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiParenthesizedExpression) {
      PsiExpression expr = ((PsiParenthesizedExpression)initializer).getExpression();
      if (expr == null) return true;
      return canInlineParmOrThisVariable(expr, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      if (operand == null) return false;
      return canInlineParmOrThisVariable(operand, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiBinaryExpression) {
      PsiBinaryExpression binExpr = (PsiBinaryExpression)initializer;
      PsiExpression lOperand = binExpr.getLOperand();
      PsiExpression rOperand = binExpr.getROperand();
      if (rOperand == null) return false;
      return canInlineParmOrThisVariable(lOperand, shouldBeFinal, strictlyFinal, accessCount, false)
             && canInlineParmOrThisVariable(rOperand, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    else if (initializer instanceof PsiThisExpression) {
      return true;
    }
    else if (initializer instanceof PsiSuperExpression) {
      return true;
    }
    else {
      return false;
    }
  }

  private static void declareUsedLocalsFinal(PsiElement expr, boolean strictlyFinal) throws IncorrectOperationException {
    if (expr instanceof PsiReferenceExpression) {
      PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        if (strictlyFinal || RefactoringUtil.canBeDeclaredFinal((PsiVariable)refElement)) {
          ((PsiVariable)refElement).getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
      }
    }
    PsiElement[] children = expr.getChildren();
    for (PsiElement child : children) {
      declareUsedLocalsFinal(child, strictlyFinal);
    }
  }

  /*
  private boolean isFieldNonModifiable(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.FINAL)){
      return true;
    }
    PsiElement[] refs = myManager.getSearchHelper().findReferences(field, null, false);
    for(int i = 0; i < refs.length; i++){
      PsiReferenceExpression ref = (PsiReferenceExpression)refs[i];
      if (PsiUtil.isAccessedForWriting(ref)) {
        PsiElement container = ref.getParent();
        while(true){
          if (container instanceof PsiMethod ||
            container instanceof PsiField ||
            container instanceof PsiClassInitializer ||
            container instanceof PsiFile) break;
          container = container.getParent();
        }
        if (container instanceof PsiMethod && ((PsiMethod)container).isConstructor()) continue;
        return false;
      }
    }
    return true;
  }
  */

  private void inlineResultVariable(PsiVariable resultVar) throws IncorrectOperationException {
    PsiReference[] refs = myManager.getSearchHelper().findReferences(resultVar,
                                                                     GlobalSearchScope.projectScope(myProject),
                                                                     false);
    PsiAssignmentExpression assignment = null;
    PsiReferenceExpression resultUsage = null;
    for (PsiReference ref1 : refs) {
      PsiReferenceExpression ref = (PsiReferenceExpression)ref1;
      if (ref.getParent() instanceof PsiAssignmentExpression
          && ((PsiAssignmentExpression)ref.getParent()).getLExpression().equals(ref)) {
        if (assignment != null) {
          assignment = null;
          break;
        }
        else {
          assignment = (PsiAssignmentExpression)ref.getParent();
        }
      }
      else {
        LOG.assertTrue(resultUsage == null);
        resultUsage = ref;
      }
    }

    if (assignment == null) return;
    boolean condition = assignment.getParent() instanceof PsiExpressionStatement;
    LOG.assertTrue(condition);
    // SCR3175 fixed: inline only if declaration and assignment is in the same code block.
    if (!(assignment.getParent().getParent() == resultVar.getParent().getParent())) return;
    if (resultUsage != null) {
      String name = resultVar.getName();
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(
        name, resultVar.getType(), assignment.getRExpression());
      declaration = (PsiDeclarationStatement)assignment.getParent().replace(declaration);
      resultVar.getParent().delete();
      resultVar = (PsiVariable)declaration.getDeclaredElements()[0];

      PsiElement parentStatement = RefactoringUtil.getParentStatement(resultUsage, true);
      PsiElement next = declaration.getNextSibling();
      boolean canInline = false;
      while (true) {
        if (next == null) break;
        if (parentStatement.equals(next)) {
          canInline = true;
          break;
        }
        if (next instanceof PsiStatement) break;
        next = next.getNextSibling();
      }

      if (canInline) {
        final PsiExpression initializer = resultVar.getInitializer();
        final PsiClass thisClass = ChangeContextUtil.getThisClass(initializer);
        ChangeContextUtil.encodeContextInfo(initializer, false);
        final PsiElement element = resultUsage.replace(resultVar.getInitializer());
        ChangeContextUtil.decodeContextInfo(element, thisClass,
                                            element.getManager().getElementFactory().createExpressionFromText("this",
                                                                                                              null));
        declaration.delete();
      }
    }
    else {
      PsiExpression rExpression = assignment.getRExpression();
      while(rExpression instanceof PsiReferenceExpression) rExpression = ((PsiReferenceExpression)rExpression).getQualifierExpression();
      if (rExpression == null || !PsiUtil.isStatement(rExpression)) {
        assignment.delete();
      } else {
        assignment.replace(rExpression);
      }
      resultVar.delete();
    }
  }

  private static final Key<String> MARK_KEY = Key.create("");

  private PsiReferenceExpression[] addBracesWhenNeeded(PsiReferenceExpression[] refs)
    throws IncorrectOperationException {
    ArrayList<PsiReferenceExpression> refsVector = new ArrayList<PsiReferenceExpression>();
    ArrayList<PsiBlockStatement> addedBracesVector = new ArrayList<PsiBlockStatement>();
    myAddedClassInitializers = new HashMap<PsiField,PsiClassInitializer>();

    for (PsiReferenceExpression ref : refs) {
      ref.putCopyableUserData(MARK_KEY, "");
    }

    RefLoop:
    for (PsiReferenceExpression ref : refs) {
      if (!ref.isValid()) continue;

      PsiElement parentStatement = RefactoringUtil.getParentStatement(ref, true);
      if (parentStatement != null) {
        PsiElement parent = ref.getParent();
        while (!parent.equals(parentStatement)) {
          if (parent instanceof PsiStatement && !(parent instanceof PsiDeclarationStatement)) {
            String text = "{\n}";
            PsiBlockStatement blockStatement = (PsiBlockStatement)myFactory.createStatementFromText(text, null);
            blockStatement = (PsiBlockStatement)myCodeStyleManager.reformat(blockStatement);
            blockStatement.getCodeBlock().add(parent);
            blockStatement = (PsiBlockStatement)parent.replace(blockStatement);

            PsiElement newStatement = blockStatement.getCodeBlock().getStatements()[0];
            addMarkedElements(refsVector, newStatement);
            addedBracesVector.add(blockStatement);
            continue RefLoop;
          }
          parent = parent.getParent();
        }
      }
      else {
        final PsiField field = PsiTreeUtil.getParentOfType(ref, PsiField.class);
        if (field != null) {
          field.normalizeDeclaration();
          final PsiExpression initializer = field.getInitializer();
          LOG.assertTrue(initializer != null);
          PsiClassInitializer classInitializer = myFactory.createClassInitializer();
          final PsiClass containingClass = field.getContainingClass();
          classInitializer = (PsiClassInitializer)containingClass.addAfter(classInitializer, field);
          final PsiCodeBlock body = classInitializer.getBody();
          PsiExpressionStatement statement = (PsiExpressionStatement)myFactory.createStatementFromText(field.getName() + " = 0;", body);
          statement = (PsiExpressionStatement)body.add(statement);
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
          assignment.getLExpression().replace(RenameUtil.createFieldReference(field, assignment));
          assignment.getRExpression().replace(initializer);
          addMarkedElements(refsVector, statement);
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            classInitializer.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
          }
          myAddedClassInitializers.put(field, classInitializer);
          continue;
        }
      }

      refsVector.add(ref);
    }

    for (PsiReferenceExpression ref : refs) {
      ref.putCopyableUserData(MARK_KEY, null);
    }

    myAddedBraces = addedBracesVector.toArray(new PsiBlockStatement[addedBracesVector.size()]);
    return refsVector.toArray(new PsiReferenceExpression[refsVector.size()]);
  }

  private static void addMarkedElements(final List<PsiReferenceExpression> array, PsiElement scope) {
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        if (element.getCopyableUserData(MARK_KEY) != null) {
          array.add((PsiReferenceExpression)element);
          element.putCopyableUserData(MARK_KEY, null);
        }
        super.visitElement(element);
      }
    });
  }

  private void removeAddedBracesWhenPossible() throws IncorrectOperationException {
    if (myAddedBraces == null) return;

    for (PsiBlockStatement blockStatement : myAddedBraces) {
      PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length == 1) {
        blockStatement.replace(statements[0]);
      }
    }

    final Set<PsiField> fields = myAddedClassInitializers.keySet();

    for (PsiField psiField : fields) {
      final PsiClassInitializer classInitializer = myAddedClassInitializers.get(psiField);
      final PsiExpression initializer = getSimpleFieldInitializer(psiField, classInitializer);
      if (initializer != null) {
        psiField.getInitializer().replace(initializer);
        classInitializer.delete();
      }
      else {
        psiField.getInitializer().delete();
      }
    }
  }

  private PsiExpression getSimpleFieldInitializer(PsiField field, PsiClassInitializer initializer) {
    final PsiStatement[] statements = initializer.getBody().getStatements();
    if (statements.length != 1) return null;
    if (!(statements[0] instanceof PsiExpressionStatement)) return null;
    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiAssignmentExpression)) return null;
    final PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
    if (!(lExpression instanceof PsiReferenceExpression)) return null;
    final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
    if (!myManager.areElementsEquivalent(field, resolved)) return null;
    return ((PsiAssignmentExpression)expression).getRExpression();
  }

  public static boolean checkBadReturns(PsiMethod method) {
    PsiReturnStatement[] returns = RefactoringUtil.findReturnStatements(method);
    if (returns.length == 0) return false;
    PsiCodeBlock body = method.getBody();
    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Control flow:");
      LOG.debug(controlFlow.toString());
    }

    List<Instruction> instructions = new ArrayList<Instruction>(controlFlow.getInstructions());

    // temporary replace all return's with empty statements in the flow
    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getStartOffset(aReturn);
      int endOffset = controlFlow.getEndOffset(aReturn);
      while (offset <= endOffset && !(instructions.get(offset) instanceof GoToInstruction)) {
        offset++;
      }
      LOG.assertTrue(instructions.get(offset) instanceof GoToInstruction);
      instructions.set(offset, EmptyInstruction.INSTANCE);
    }

    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getEndOffset(aReturn);
      while (true) {
        if (offset == instructions.size()) break;
        Instruction instruction = instructions.get(offset);
        if (instruction instanceof GoToInstruction) {
          offset = ((GoToInstruction)instruction).offset;
        }
        else if (instruction instanceof ThrowToInstruction) {
          offset = ((ThrowToInstruction)instruction).offset;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          // In case of "conditional throw to", control flow will not be altered
          // If exception handler is in method, we will inline it to ivokation site
          // If exception handler is at invocation site, execution will continue to get there
          offset++;
        }
        else {
          return true;
        }
      }
    }

    return false;
  }

  private static class BlockData {
    final PsiCodeBlock block;
    final PsiLocalVariable thisVar;
    final PsiLocalVariable[] parmVars;
    final PsiLocalVariable resultVar;

    public BlockData(PsiCodeBlock block,
                     PsiLocalVariable thisVar,
                     PsiLocalVariable[] parmVars,
                     PsiLocalVariable resultVar) {
      this.block = block;
      this.thisVar = thisVar;
      this.parmVars = parmVars;
      this.resultVar = resultVar;
    }
  }
}