
package com.intellij.refactoring.encapsulateFields;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor implements EncapsulateFieldsDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor");

  private PsiClass myClass;
  private EncapsulateFieldsDialog myDialog;
  private PsiField[] myFields;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project) {
    super(project);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    PsiField[] fields = new PsiField[myFields.length];
    for (int idx = 0; idx < myFields.length; idx++) {
      fields[idx] = myFields[idx];
    }
    return new EncapsulateFieldsViewDescriptor(fields, usages, refreshCommand);
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myDialog.isPreviewUsages();
    return super.isPreviewUsages(usages) || toPreview;
  }

  protected String getCommandName() {
    return "Encapsulating fields in " + UsageViewUtil.getDescriptiveName(myClass);
  }

  public void run(EncapsulateFieldsDialog dialog) {
    myDialog = dialog;
    run((Object)null);
  }

  public void run(Object markerId) {
    myFields = myDialog.getSelectedFields();
    if (myFields.length == 0){
      String message = "No fields selected";
      RefactoringMessageUtil.showErrorMessage(EncapsulateFieldsHandler.REFACTORING_NAME, message, HelpID.ENCAPSULATE_FIELDS, myProject);
      return;
    }
    myClass = myFields[0].getContainingClass();

    super.run(markerId);
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    ArrayList<String> conflicts = new ArrayList<String>();

    if (myDialog != null) {
      checkExistingMethods(myDialog.getGetterPrototypes(), conflicts, "getter");
      checkExistingMethods(myDialog.getSetterPrototypes(), conflicts, "setter");

      if(conflicts.size() > 0) {
        ConflictsDialog dialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]), myProject);
        dialog.show();
        if(!dialog.isOK()) return false;
      }
      // make sure that dialog is closed in swing thread
      ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
        public void run() {
          myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      });
    }

    return true;
  }

  private void checkExistingMethods(PsiMethod[] prototypes, ArrayList<String> conflicts, String methodRole) {
    if(prototypes == null) return;
    for (int i = 0; i < prototypes.length; i++) {
      PsiMethod prototype = prototypes[i];
      final PsiType prototypeReturnType = prototype.getReturnType();
      PsiMethod existing = myClass.findMethodBySignature(prototype, true);
      if(existing != null) {
        final PsiType returnType = existing.getReturnType();
        if(!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
          final String descr = PsiFormatUtil.formatMethod(existing,
              PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_TYPE,
                            PsiFormatUtil.SHOW_TYPE
                    );
          String message = "There already is a method " + ConflictsUtil.htmlEmphasize(descr) + " which differs from "
                  + methodRole + " " + ConflictsUtil.htmlEmphasize(prototype.getName()) + " by return type only.";
          conflicts.add(message);
        }
      }
    }
  }

  protected UsageInfo[] findUsages() {
    boolean findGet = myDialog.isToEncapsulateGet();
    boolean findSet = myDialog.isToEncapsulateSet();
    PsiModifierList newModifierList = null;
    if (!myDialog.isToUseAccessorsWhenAccessible()){
      PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      try{
        PsiField field = factory.createField("a", PsiType.INT);
        setNewFieldVisibility(field);
        newModifierList = field.getModifierList();
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }
    PsiMethod[] getterPrototypes = myDialog.getGetterPrototypes();
    PsiMethod[] setterPrototypes = myDialog.getSetterPrototypes();
    ArrayList<UsageInfo> array = new ArrayList<UsageInfo>();
    PsiField[] fields = myFields;
    for(int i = 0; i < fields.length; i++){
      PsiField field = fields[i];
      PsiSearchHelper helper = field.getManager().getSearchHelper();
      PsiReference[] refs = helper.findReferences(field, GlobalSearchScope.projectScope(myProject), false);
      for(int j = 0; j < refs.length; j++){
        final PsiReference reference = refs[j];
        if (!(reference instanceof PsiReferenceExpression)) continue;
        PsiReferenceExpression ref = (PsiReferenceExpression)reference;
        // [Jeka] to avoid recursion in the field's accessors
        if (findGet && isUsedInExistingAccessor(getterPrototypes[i], ref)) continue;
        if (findSet && isUsedInExistingAccessor(setterPrototypes[i], ref)) continue;
        if (!findGet){
          if (!PsiUtil.isAccessedForWriting(ref)) continue;
        }
        if (!findSet || field.hasModifierProperty(PsiModifier.FINAL)){
          if (!PsiUtil.isAccessedForReading(ref)) continue;
        }
        if (!myDialog.isToUseAccessorsWhenAccessible()){
          PsiClass accessObjectClass = null;
          PsiExpression qualifier = ref.getQualifierExpression();
          if (qualifier != null){
            accessObjectClass = (PsiClass) PsiUtil.getAccessObjectClass(qualifier).getElement();
          }
          if (PsiManager.getInstance(myProject).getResolveHelper().isAccessible(field, newModifierList, ref, accessObjectClass)) continue;
        }
        UsageInfo usageInfo = new MyUsageInfo(ref, i);
        array.add(usageInfo);
      }
    }
    MyUsageInfo[] usageInfos = array.toArray(new MyUsageInfo[array.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myFields.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFields[idx] = (PsiField)element;
    }

    myClass = myFields[0].getContainingClass();
  }

  protected void performRefactoring(UsageInfo[] usages) {
    // change visibility of fields
    if (myDialog.getFieldsVisibility() != null){
      // "as is"
      for(int i = 0; i < myFields.length; i++){
        setNewFieldVisibility(myFields[i]);
      }
    }

    // generate accessors
    myNameToGetter = new com.intellij.util.containers.HashMap<String, PsiMethod>();
    myNameToSetter = new com.intellij.util.containers.HashMap<String, PsiMethod>();
    for(int i = 0; i < myFields.length; i++){
      PsiField field = myFields[i];
      if (myDialog.isToEncapsulateGet()){
        PsiMethod[] prototypes = myDialog.getGetterPrototypes();
        addOrChangeAccessor(prototypes[i], myNameToGetter);
      }
      if (myDialog.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL)){
        PsiMethod[] prototypes = myDialog.getSetterPrototypes();
        addOrChangeAccessor(prototypes[i], myNameToSetter);
      }
    }

    HashSet<PsiElement> allFiles = new HashSet<PsiElement>();
    HashMap<PsiElement,MyUsageInfo> treeSkeletonMap = new com.intellij.util.containers.HashMap<PsiElement, MyUsageInfo>();
    for(int i = 0; i < usages.length; i++){
      MyUsageInfo usage = (MyUsageInfo)usages[i];
      if (!usage.getElement().isValid()) continue;
      PsiElement element = usage.getElement();
      treeSkeletonMap.put(element, usage);
      while(!(element instanceof PsiFile)){
        element = element.getParent();
        if (!treeSkeletonMap.containsKey(element)){
          treeSkeletonMap.put(element, null);
        }
      }
      allFiles.add(element);
    }

    for(Iterator<PsiElement> iterator = allFiles.iterator(); iterator.hasNext();){
      PsiFile file = (PsiFile)iterator.next();
      processSubtree(file, treeSkeletonMap);
    }
  }

  private void setNewFieldVisibility(PsiField field) {
    try{
      if (myDialog.getFieldsVisibility() != null){
        field.getModifierList().setModifierProperty(myDialog.getFieldsVisibility(), true);
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private void addOrChangeAccessor(PsiMethod prototype, HashMap<String,PsiMethod> nameToAncestor) {
    PsiMethod existing = myClass.findMethodBySignature(prototype, false);
    PsiMethod result = existing;
    try{
      if (existing == null){
        prototype.getModifierList().setModifierProperty(myDialog.getAccessorsVisibility(), true);
        result = (PsiMethod) myClass.add(prototype);
      }
      else{
        //TODO : change visibility
      }
      nameToAncestor.put(prototype.getName(), result);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private boolean isUsedInExistingAccessor(PsiMethod prototype, PsiElement element) {
    PsiMethod existingAccessor = myClass.findMethodBySignature(prototype, false);
    if (existingAccessor != null) {
      PsiElement parent = element;
      while (parent != null) {
        if (existingAccessor.equals(parent)) return true;
        parent = parent.getParent();
      }
    }
    return false;
  }

  private void processSubtree(PsiElement root, HashMap<PsiElement,MyUsageInfo> treeSkeletonMap) {
    if (!treeSkeletonMap.containsKey(root)) return;
    if (root instanceof PsiAssignmentExpression){
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)root;
      PsiExpression rExpr = assignment.getRExpression();
      if (rExpr != null){
        processSubtree(rExpr, treeSkeletonMap);
      }
      processSubtree(assignment.getLExpression(), treeSkeletonMap);
    }
    else{
      PsiElement[] children = root.getChildren();
      for(int i = 0; i < children.length; i++){
        processSubtree(children[i], treeSkeletonMap);
      }
    }
    MyUsageInfo usage = treeSkeletonMap.get(root);
    if (usage != null){
      processUsage(usage);
    }
  }

  private void processUsage(MyUsageInfo usage) {
    PsiField field = myFields[usage.fieldIndex];
    boolean processGet = myDialog.isToEncapsulateGet();
    boolean processSet = myDialog.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return;
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();

    try{
      final PsiReferenceExpression expr = (PsiReferenceExpression)usage.getElement();
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression)parent).getLExpression())){
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() == null) return;
        PsiJavaToken opSign = assignment.getOperationSign();
        IElementType opType = opSign.getTokenType();
        if (opType == JavaTokenType.EQ) {
          {
            if (!processSet) return;
            final int fieldIndex = usage.fieldIndex;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldIndex, setterArgument, expr);

            if (methodCall != null) {
              assignment.replace(methodCall);
            }
            //TODO: check if value is used!!!
          }
        }
        else if (opType == JavaTokenType.ASTERISKEQ || opType == JavaTokenType.DIVEQ || opType == JavaTokenType.PERCEQ ||
                 opType == JavaTokenType.PLUSEQ ||
                 opType == JavaTokenType.MINUSEQ ||
                 opType == JavaTokenType.LTLTEQ ||
                 opType == JavaTokenType.GTGTEQ ||
                 opType == JavaTokenType.GTGTGTEQ ||
                 opType == JavaTokenType.ANDEQ ||
                 opType == JavaTokenType.OREQ ||
                 opType == JavaTokenType.XOREQ) {
          {
            // Q: side effects of qualifier??!

            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            PsiExpression getExpr = expr;
            if (processGet) {
              final int fieldIndex = usage.fieldIndex;
              final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            String text = "a" + opName + "b";
            PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, expr);
            binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
            binExpr.getLOperand().replace(getExpr);
            binExpr.getROperand().replace(assignment.getRExpression());

            PsiExpression setExpr;
            if (processSet) {
              PsiMethodCallExpression methodCall = createSetterCall(usage.fieldIndex, binExpr, expr);
              setExpr = methodCall;
            }
            else {
              text = "a = b";
              PsiAssignmentExpression assignment1 = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
              assignment1 = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment1);
              assignment1.getLExpression().replace(expr);
              assignment1.getRExpression().replace(binExpr);
              setExpr = assignment1;
            }

            assignment.replace(setExpr);
            //TODO: check if value is used!!!
          }
        }
      }
      else if (isPlusPlusOrMinusMinus(parent)){
        PsiJavaToken sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationSign();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationSign();
        }
        IElementType tokenType = sign.getTokenType();

        PsiExpression getExpr = expr;
        if (processGet){
          final int fieldIndex = usage.fieldIndex;
          final PsiMethodCallExpression getterCall = createGetterCall(fieldIndex, expr);
          if(getterCall != null) {
            getExpr = getterCall;
          }
        }

        String text;
        if (tokenType == JavaTokenType.PLUSPLUS){
          text = "a+1";
        }
        else{
          text = "a-1";
        }
        PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, null);
        binExpr = (PsiBinaryExpression)CodeStyleManager.getInstance(myProject).reformat(binExpr);
        binExpr.getLOperand().replace(getExpr);

        PsiExpression setExpr;
        if (processSet){
          final int fieldIndex = usage.fieldIndex;
          final PsiMethodCallExpression setterCall = createSetterCall(fieldIndex, binExpr, expr);
          setExpr = setterCall;
        }
        else{
          text = "a = b";
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignment);
          assignment.getLExpression().replace(expr);
          assignment.getRExpression().replace(binExpr);
          setExpr = assignment;
        }
        parent.replace(setExpr);
      }
      else{
        if (!processGet) return;
        PsiMethodCallExpression methodCall = createGetterCall(usage.fieldIndex, expr);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  private PsiMethodCallExpression createSetterCall(final int fieldIndex, final PsiExpression setterArgument, PsiReferenceExpression expr) throws IncorrectOperationException {
    String[] setterNames = myDialog.getSetterNames();
    PsiElementFactory factory = expr.getManager().getElementFactory();
    final String setterName = setterNames[fieldIndex];
    String text = setterName + "(a)";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    methodCall.getArgumentList().getExpressions()[0].replace(setterArgument);
    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }
    final PsiMethod targetMethod = myNameToSetter.get(setterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
    }
    return methodCall;
  }

  private PsiMethodCallExpression createGetterCall(final int fieldIndex, PsiReferenceExpression expr)
          throws IncorrectOperationException {
    String[] getterNames = myDialog.getGetterNames();
    PsiElementFactory factory = expr.getManager().getElementFactory();
    final String getterName = getterNames[fieldIndex];
    String text = getterName + "()";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);
    methodCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(methodCall);

    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }

    final PsiMethod targetMethod = myNameToGetter.get(getterName);
    methodCall = checkMethodResolvable(methodCall, targetMethod, expr);
    if(methodCall == null) {
      VisibilityUtil.escalateVisibility(myFields[fieldIndex], expr);
    }
    return methodCall;
  }

  private PsiMethodCallExpression checkMethodResolvable(PsiMethodCallExpression methodCall, final PsiMethod targetMethod, PsiReferenceExpression context) throws IncorrectOperationException {
    PsiElementFactory factory = targetMethod.getManager().getElementFactory();
    final PsiElement resolved = methodCall.getMethodExpression().resolve();
    if (resolved != targetMethod) {
      LOG.assertTrue(resolved instanceof PsiMethod);
      final PsiClass containingClass = ((PsiMethod) resolved).getContainingClass();
      if(containingClass.isInheritor(myClass, false)) {
        final PsiExpression newMethodExpression =
                factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getMethodExpression().replace(newMethodExpression);
      } else {
        methodCall = null;
      }
    }
    return methodCall;
  }

  private boolean isPlusPlusOrMinusMinus(PsiElement expression) {
    if (expression instanceof PsiPrefixExpression){
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      PsiJavaToken sign = prefixExpression.getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    if (expression instanceof PsiPostfixExpression){
      PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      PsiJavaToken sign = postfixExpression.getOperationSign();
      IElementType tokenType = sign.getTokenType();
      return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
    }
    return false;
  }

  private static class MyUsageInfo extends UsageInfo {
    public final int fieldIndex;

    public MyUsageInfo(PsiJavaCodeReferenceElement ref, int fieldIndex) {
      super(ref);
      this.fieldIndex = fieldIndex;
    }
  }
}