package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.*;

public class InlineMethodProcessor extends BaseRefactoringProcessor implements InlineMethodDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineMethodProcessor");

  private PsiMethod myMethod;
  private PsiJavaCodeReferenceElement myReference;
  private Editor myEditor;
  private InlineOptions myDialog;

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
                               Editor editor) {
    super(project);
    myMethod = method;
    myReference = reference;
    myEditor = editor;

    myManager = PsiManager.getInstance(myProject);
    myFactory = myManager.getElementFactory();
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    myDescriptiveName = UsageViewUtil.getDescriptiveName(myMethod);
  }

  protected String getCommandName() {
    return "Inlining method " + myDescriptiveName;
  }

  public void run(InlineMethodDialog dialog) {
    myDialog = dialog;
    this.run((Object)null);
  }

  public void testRun(InlineOptions dialog) {
    myDialog = dialog;
    UsageInfo[] usages = findUsages();
    performRefactoring(usages);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new InlineViewDescriptor(myMethod, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    if (myDialog.isInlineThisOnly()) return new UsageInfo[]{new UsageInfo(myReference)};
    PsiSearchHelper helper = myManager.getSearchHelper();
    PsiReference[] refs = helper.findReferences(myMethod, GlobalSearchScope.projectScope(myProject), true);
    UsageInfo[] infos = new UsageInfo[refs.length];
    for (int i = 0; i < refs.length; i++) {
      infos[i] = new UsageInfo(refs[i].getElement());
    }
    return infos;
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myMethod = (PsiMethod)elements[0];
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    myMethod.accept(collector);
    final HashMap<PsiMember,HashSet<PsiMember>> containersToReferenced;
    String fromForReference = null;
    if (usages[0] != null) {
      containersToReferenced = getInaccessible(collector.myReferencedMembers, usages[0]);
    }
    else {
      containersToReferenced = getInaccessible(collector.myReferencedMembers,
                                               new UsageInfo[]{new UsageInfo(myReference)});
      fromForReference = ConflictsUtil.getDescription(ConflictsUtil.getContainer(myReference), true);
    }
    ArrayList<String> conflicts = new ArrayList<String>();
    final Set<PsiMember> containers = containersToReferenced.keySet();
    for (Iterator<PsiMember> iterator = containers.iterator(); iterator.hasNext();) {
      PsiMember container = iterator.next();
      HashSet<PsiMember> referencedInaccessible = containersToReferenced.get(container);
      for (Iterator<PsiMember> iterator1 = referencedInaccessible.iterator(); iterator1.hasNext();) {
        PsiElement referenced = iterator1.next();
        String message = ConflictsUtil.getDescription(referenced, true) + " that is used in inlined method, " +
                         " is not accessible from " +
                         (fromForReference == null ?
                          "call site(s) in " + ConflictsUtil.getDescription(container, true) : fromForReference);
        conflicts.add(ConflictsUtil.capitalize(message));
      }
    }

    if (myDialog != null && conflicts.size() > 0) {
      ConflictsDialog dialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]),
                                                   myProject);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    // make sure that dialog is closed in swing thread
    ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
      public void run() {
        myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    });
    return true;
  }

  /**
   * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
   * to subsets of referencedElemens that are not accessible from that container
   *
   * @param referencedElements
   * @param usages
   * @return
   */
  private static HashMap<PsiMember,HashSet<PsiMember>> getInaccessible(HashSet<PsiMember> referencedElements, UsageInfo[] usages) {
    HashMap<PsiMember,HashSet<PsiMember>> result = new com.intellij.util.containers.HashMap<PsiMember, HashSet<PsiMember>>();

    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      final PsiMember container = ConflictsUtil.getContainer(usage.getElement());
      HashSet<PsiMember> inaccessibleReferenced = result.get(container);
      if (inaccessibleReferenced == null) {
        inaccessibleReferenced = new HashSet<PsiMember>();
        result.put(container, inaccessibleReferenced);
        for (Iterator<PsiMember> iterator = referencedElements.iterator(); iterator.hasNext();) {
          PsiMember member = iterator.next();
          if (!PsiUtil.isAccessible(member, usage.getElement(), null)) {
            inaccessibleReferenced.add(member);
          }
        }
      }
    }

    return result;
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    //if (myDialog.isInlineThisOnly()) return false;
    boolean toPreview = myDialog.isPreviewUsages();
    if (UsageViewUtil.hasReadOnlyUsages(usages)) {
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
    }
    return toPreview;
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
      if (myDialog.isInlineThisOnly()) {
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
      }
      else {
        if (myMethod.isConstructor()) {
          for (int i = 0; i < usages.length; i++) {
            PsiElement element = usages[i].getElement();
            if (element instanceof PsiJavaCodeReferenceElement) {
              PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)element);
              if (constructorCall != null) {
                inlineConstructorCall(constructorCall);
              }
            }
          }
          myMethod.delete();
        }
        else {
          ArrayList<PsiReferenceExpression> tempRefs = new ArrayList<PsiReferenceExpression>();
          for (int i = 0; i < usages.length; i++) {
            final UsageInfo usage = usages[i];
            if (usage.getElement() instanceof PsiReferenceExpression) {
              tempRefs.add((PsiReferenceExpression)usage.getElement());
            }
          }
          PsiReferenceExpression[] refs = tempRefs.toArray(
            new PsiReferenceExpression[tempRefs.size()]);
          refs = addBracesWhenNeeded(refs);
          for (int i = 0; i < refs.length; i++) {
            inlineMethodCall(refs[i]);
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

  private void inlineConstructorCall(PsiCall constructorCall) {
    final PsiMethod oldConstructor = constructorCall.resolveMethod();
    if (oldConstructor == null) return;
    final PsiManager manager = oldConstructor.getManager();
    final PsiExpression[] instanceCreationArguments = constructorCall.getArgumentList().getExpressions();
    final PsiParameter[] parameters = oldConstructor.getParameterList().getParameters();
    if (parameters.length != instanceCreationArguments.length) return;

    PsiCodeBlock body = oldConstructor.getBody();
    if (body == null) return;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiExpressionStatement)) return;
    PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return;

    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
    if (methodExpression != null && "this".equals(methodExpression.getReferenceName())) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression.copy();
      final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        arg.accept(new PsiRecursiveElementVisitor() {
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            PsiElement resolved = expression.resolve();
            //For some unknown reason declarationScope != oldConstructor, though equivalent
            if (resolved instanceof PsiParameter && manager.areElementsEquivalent(((PsiParameter)resolved).getDeclarationScope(), oldConstructor)) {
            PsiElement declarationScope = ((PsiParameter)resolved).getDeclarationScope();
              PsiParameter[] declarationParameters = ((PsiMethod)declarationScope).getParameterList().getParameters();
              for (int j = 0; j < declarationParameters.length; j++) {
                if (declarationParameters[j] == resolved) {
                  try {
                    expression.replace(instanceCreationArguments[j]);
                    break;
                  }
                  catch (IncorrectOperationException e) { LOG.error(e); }
                }
              }
            }
          }
        });
      }
      try {
        constructorCall.getArgumentList().replace(methodCall.getArgumentList());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    BlockData blockData = prepareBlock(ref);
    solveVariableNameConflicts(blockData.block, ref);
    substituteMethodTypeParams(blockData.block, methodCall);
    addParmAndThisVarInitializers(blockData, methodCall);

    PsiElement anchor = RefactoringUtil.getParentStatement(methodCall, true);
    if (anchor == null) return; //TODO!! (JSP, field initializer)
    PsiElement anchorParent = anchor.getParent();
    PsiLocalVariable thisVar = null;
    PsiLocalVariable[] parmVars = new PsiLocalVariable[blockData.parmVars.length];
    PsiLocalVariable resultVar = null;
    PsiElement[] methodStatements = blockData.block.getChildren();
    if (methodStatements.length > 2) {
      PsiElement first = methodStatements[1];
      PsiElement last = methodStatements[methodStatements.length - 2];

      PsiStatement[] statements = blockData.block.getStatements();
      if (statements.length > 0 && statements[statements.length - 1] instanceof PsiReturnStatement) {
        last = statements[statements.length - 1].getPrevSibling();
      }

      PsiElement firstAdded = anchorParent.addRangeBefore(first, last, anchor);

      PsiElement current = firstAdded;
      if (blockData.thisVar != null) {
        while (current != null && !(current instanceof PsiStatement)) {
          current = current.getNextSibling();
        }
        if (current == null) {
          current = firstAdded;
        }
        thisVar = (PsiLocalVariable)((PsiDeclarationStatement)current).getDeclaredElements()[0];
        current = current.getNextSibling();
      }
      for (int i = 0; i < parmVars.length; i++) {
        final PsiElement oldCurrent = current;
        while (current != null && !(current instanceof PsiStatement)) {
          current = current.getNextSibling();
        }
        if (current == null) {
          current = oldCurrent;
        }
        parmVars[i] = (PsiLocalVariable)((PsiDeclarationStatement)current).getDeclaredElements()[0];
        current = current.getNextSibling();
      }
      if (blockData.resultVar != null) {
        final PsiElement oldCurrent = current;
        while (current != null && !(current instanceof PsiStatement)) {
          current = current.getNextSibling();
        }
        if (current == null) {
          current = oldCurrent;
        }
        resultVar = (PsiLocalVariable)((PsiDeclarationStatement)current).getDeclaredElements()[0];
        current = current.getNextSibling();
      }
    }

    if (methodCall.getParent() instanceof PsiExpressionStatement) {
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
    PsiExpression thisAccessExpr = thisVar != null
                                   ? myFactory.createExpressionFromText(thisVar.getName(), null)
                                   : null;
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

  private void substituteMethodTypeParams(PsiCodeBlock block, PsiMethodCallExpression methodCall) {
    ResolveResult resolveResult = methodCall.getMethodExpression().advancedResolve(false);
    LOG.assertTrue (resolveResult.getElement() == myMethod);
    if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
      PsiTypeParameter[] oldTypeParameters = myMethod.getTypeParameterList().getTypeParameters();
      PsiTypeParameter[] newTypeParameters = myMethodCopy.getTypeParameterList().getTypeParameters();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      for (int i = 0; i < newTypeParameters.length; i++) {
        substitutor = substitutor.put(newTypeParameters[i], resolveResult.getSubstitutor().substitute(oldTypeParameters[i]));
      }
      substituteMethodTypeParamsInner(block, substitutor);
    }
  }

  private void substituteMethodTypeParamsInner(PsiElement scope, final PsiSubstitutor substitutor) {
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitTypeElement(PsiTypeElement typeElement) {
        PsiType type = typeElement.getType();

        if (type instanceof PsiClassType) {
          ResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
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
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      final PsiElement refElement = reference.getElement();
      final PsiElement anonymousClass = PsiTreeUtil.getParentOfType(refElement, PsiAnonymousClass.class);
      if (anonymousClass != null && PsiTreeUtil.isAncestor(myMethod, anonymousClass, true)) {
        return true;
      }
    }
    return false;
  }


  private boolean syncNeeded (final Object thisVar, final PsiReferenceExpression ref) {
    if (thisVar == null) return false;
    if (!myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    final PsiMethod containingMethod = Util.getContainingMethod(ref);
    if (containingMethod == null) return true;
    if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return true;
    final PsiClass sourceContainingClass = myMethod.getContainingClass();
    final PsiClass targetContainingClass = containingMethod.getContainingClass();
    if (sourceContainingClass.equals (targetContainingClass)) return false;
    return true;
  }

  private BlockData prepareBlock(PsiReferenceExpression ref) throws IncorrectOperationException {
    ChangeContextUtil.encodeContextInfo(myMethod, false);
    myMethodCopy = (PsiMethod)myMethod.copy();

    ChangeContextUtil.clearContextInfo(myMethod);
    final PsiCodeBlock block = myMethodCopy.getBody();
    final PsiStatement[] originalStatements = block.getStatements();

    PsiLocalVariable resultVar = null;
    PsiType returnType = myMethod.getReturnType();
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
      String defaultValue = CodeInsightUtil.getDefaultValueOfType(parm.getType());
      PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(name, parm.getType(),
                                                                                         initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parmVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      if (parm.hasModifierProperty(PsiModifier.FINAL)) {
        parmVars[i].getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }
    }

    PsiLocalVariable thisVar = null;
    if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      String thisVarName;
      PsiClass containingClass = myMethod.getContainingClass();

      if (containingClass != null) {
        PsiType thisType = myFactory.createType(containingClass);
        String[] names = myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, thisType)
          .names;
        thisVarName = names[0];
        thisVarName = myCodeStyleManager.suggestUniqueVariableName(thisVarName, block.getFirstChild(), true);
        PsiExpression initializer = myFactory.createExpressionFromText("null", null);
        PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(thisVarName, thisType,
                                                                                           initializer);
        declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
        thisVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      }
    }

    //if (myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED) && thisVar != null)
    if (syncNeeded (thisVar, ref)) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)myFactory.createStatementFromText("synchronized(" + thisVar.getName() + "){}", block);
      synchronizedStatement =
      (PsiSynchronizedStatement)CodeStyleManager.getInstance(myProject).reformat(synchronizedStatement);
      synchronizedStatement = (PsiSynchronizedStatement)block.add(synchronizedStatement);
      final PsiCodeBlock synchronizedBody = synchronizedStatement.getBody();
      for (int i = 0; i < originalStatements.length; i++) {
        final PsiStatement originalStatement = originalStatements[i];
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
    }

    if (resultName != null) {
      PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(myMethodCopy);
      for (int i = 0; i < returnStatements.length; i++) {
        PsiReturnStatement returnStatement = returnStatements[i];
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
    for (int i = 0; i < children.length; i++) {
      solveVariableNameConflicts(children[i], placeToInsert);
    }
  }

  private void addParmAndThisVarInitializers(BlockData blockData, PsiMethodCallExpression methodCall)
    throws IncorrectOperationException {
    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for (int i = 0; i < blockData.parmVars.length; i++) {
      if (i >= args.length) break;
      blockData.parmVars[i].getInitializer().replace(args[i]);
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

  private void inlineParmOrThisVariable(PsiLocalVariable variable, boolean strictlyFinal)
    throws IncorrectOperationException {
    PsiReference[] refs = myManager.getSearchHelper().findReferences(variable,
                                                                     GlobalSearchScope.projectScope(myProject),
                                                                     false);

    if (refs.length == 0) {
      variable.getParent().delete(); //Q: side effects?
      return;
    }


    boolean isAccessedForWriting = false;
    for (int i = 0; i < refs.length; i++) {
      PsiElement refElement = refs[i].getElement();
      if (refElement instanceof PsiExpression) {
        if (PsiUtil.isAccessedForWriting(((PsiExpression)refElement))) {
          isAccessedForWriting = true;
        }
      }
    }

    PsiExpression initializer = variable.getInitializer();
    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    if (canInlineParmOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.length, isAccessedForWriting)) {
      if (shouldBeFinal) {
        declareUsedLocalsFinal(initializer, strictlyFinal);
      }
      for (int j = 0; j < refs.length; j++) {
        PsiExpression expr = RefactoringUtil.inlineVariable(variable, initializer,
                                                            (PsiJavaCodeReferenceElement)refs[j]);

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
                refExpr = (PsiReferenceExpression)refExpr.replace(exprCopy);
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
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
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

  private void declareUsedLocalsFinal(PsiElement expr, boolean strictlyFinal) throws IncorrectOperationException {
    if (expr instanceof PsiReferenceExpression) {
      PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        if (strictlyFinal || RefactoringUtil.canBeDeclaredFinal((PsiVariable)refElement)) {
          ((PsiVariable)refElement).getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
      }
    }
    PsiElement[] children = expr.getChildren();
    for (int i = 0; i < children.length; i++) {
      declareUsedLocalsFinal(children[i], strictlyFinal);
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
    boolean isAssignmentUnique = false;
    PsiReferenceExpression resultUsage = null;
    for (int i = 0; i < refs.length; i++) {
      PsiReferenceExpression ref = (PsiReferenceExpression)refs[i];
      if (ref.getParent() instanceof PsiAssignmentExpression
          && ((PsiAssignmentExpression)ref.getParent()).getLExpression().equals(ref)) {
        if (assignment != null) {
          isAssignmentUnique = false;
        }
        else {
          assignment = (PsiAssignmentExpression)ref.getParent();
          isAssignmentUnique = true;
        }
      }
      else {
        LOG.assertTrue(resultUsage == null);
        resultUsage = ref;
      }
    }

    if (!isAssignmentUnique) return;
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
      PsiExpression expr = (PsiExpression)assignment.replace(assignment.getRExpression());
      resultVar.getParent().delete();
      if (expr.getParent() instanceof PsiExpressionStatement && isSimpleExpression(expr)) {
        expr.getParent().delete();
      }
    }
  }

  private boolean isSimpleExpression(PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) {
      return true;
    }
    else if (expr instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)expr).getQualifierExpression();
      return qualifier == null || isSimpleExpression(qualifier);
    }
    else if (expr instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression accessExpr = (PsiArrayAccessExpression)expr;
      return isSimpleExpression(accessExpr.getArrayExpression()) &&
             isSimpleExpression(accessExpr.getIndexExpression());
    }
    else if (expr instanceof PsiBinaryExpression) {
      PsiBinaryExpression binExpr = (PsiBinaryExpression)expr;
      PsiExpression lOperand = binExpr.getLOperand();
      PsiExpression rOperand = binExpr.getROperand();
      if (rOperand == null) return false;
      return isSimpleExpression(lOperand) && isSimpleExpression(rOperand);
    }
    else if (expr instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    else if (expr instanceof PsiThisExpression) {
      return true;
    }
    else if (expr instanceof PsiSuperExpression) {
      return true;
    }
    else if (expr instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)expr).getOperand();
      if (operand == null) return false;
      return isSimpleExpression(operand);
    }
    else { //TODO: some other cases
      return false;
    }
  }

  private static final Key MARK_KEY = Key.create("");

  private PsiReferenceExpression[] addBracesWhenNeeded(PsiReferenceExpression[] refs)
    throws IncorrectOperationException {
    ArrayList<PsiReferenceExpression> refsVector = new ArrayList<PsiReferenceExpression>();
    ArrayList<PsiBlockStatement> addedBracesVector = new ArrayList<PsiBlockStatement>();
    myAddedClassInitializers = new HashMap<PsiField,PsiClassInitializer>();

    for (int i = 0; i < refs.length; i++) {
      refs[i].putCopyableUserData(MARK_KEY, "");
    }

    RefLoop:
      for (int i = 0; i < refs.length; i++) {
        PsiReferenceExpression ref = refs[i];
        if (!ref.isValid()) continue;

        PsiElement parentStatement = RefactoringUtil.getParentStatement(ref, true);
        if (parentStatement != null) {
          PsiElement parent = ref.getParent();
          while (!parent.equals(parentStatement)) {
            if (parent instanceof PsiStatement && !(parent instanceof PsiDeclarationStatement)) {
              String text = "{\n}";
              PsiBlockStatement blockStatement = (PsiBlockStatement)myFactory.createStatementFromText(text, null);
              blockStatement = (PsiBlockStatement)myCodeStyleManager.reformat(blockStatement);
              blockStatement = (PsiBlockStatement)parent.getParent().addAfter(blockStatement, parent);

              PsiCodeBlock body = blockStatement.getCodeBlock();
              PsiElement newStatement = body.add(parent);
              parent.delete();
              addMarkedElements(refsVector, newStatement);
              addedBracesVector.add(blockStatement);
              continue RefLoop;
            }
            parent = parent.getParent();
          }
        } else {
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
            continue RefLoop;
          }
        }

        refsVector.add(ref);
      }

    for (int i = 0; i < refs.length; i++) {
      refs[i].putCopyableUserData(MARK_KEY, null);
    }

    myAddedBraces = addedBracesVector.toArray(new PsiBlockStatement[addedBracesVector.size()]);
    return refsVector.toArray(new PsiReferenceExpression[refsVector.size()]);
  }

  private void addMarkedElements(ArrayList array, PsiElement scope) {
    if (scope.getCopyableUserData(MARK_KEY) != null) {
      array.add(scope);
      scope.putCopyableUserData(MARK_KEY, null);
    }
    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      addMarkedElements(array, child);
    }
  }

  private void removeAddedBracesWhenPossible() throws IncorrectOperationException {
    if (myAddedBraces == null) return;

    for (int i = 0; i < myAddedBraces.length; i++) {
      PsiBlockStatement blockStatement = myAddedBraces[i];
      PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length == 1) {
        blockStatement.replace(statements[0]);
      }
    }

    final Set<PsiField> fields = myAddedClassInitializers.keySet();
    for (Iterator<PsiField> iterator = fields.iterator(); iterator.hasNext();) {
      PsiField psiField = iterator.next();
      final PsiClassInitializer classInitializer = myAddedClassInitializers.get(psiField);
      final PsiExpression initializer = getSimpleFieldInitializer(psiField, classInitializer);
      if (initializer != null) {
        psiField.getInitializer().replace(initializer);
        classInitializer.delete();
      } else {
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
      controlFlow = new ControlFlowAnalyzer(body, new LocalsControlFlowPolicy(body), false).buildControlFlow();
    }
    catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Control flow:");
      LOG.debug(controlFlow.toString());
    }

    Instruction[] instructions = controlFlow.getInstructions();

    // temporary replace all return's with empty statements in the flow
    for (int i = 0; i < returns.length; i++) {
      PsiReturnStatement aReturn = returns[i];
      int offset = controlFlow.getStartOffset(aReturn);
      int endOffset = controlFlow.getEndOffset(aReturn);
      while (offset <= endOffset && !(instructions[offset] instanceof GoToInstruction)) {
        offset++;
      }
      LOG.assertTrue(instructions[offset] instanceof GoToInstruction);
      instructions[offset] = new EmptyInstruction();
    }

    for (int i = 0; i < returns.length; i++) {
      PsiReturnStatement aReturn = returns[i];
      int offset = controlFlow.getEndOffset(aReturn);
      while (true) {
        if (offset == instructions.length) break;
        Instruction instruction = instructions[offset];
        if ((instruction instanceof GoToInstruction)) {
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