/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveMembers;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

public class MoveMembersProcessor extends BaseRefactoringProcessor implements MoveMembersDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveMembers.MoveMembersProcessor");

  private PsiClass myTargetClass;
  private MoveMembersOptions myDialog;
  private LinkedHashSet<PsiMember> myMembersToMove = new LinkedHashSet<PsiMember>();
  private MoveCallback myMoveCallback;
  private String myNewVisibility; // "null" means "as is"

  public MoveMembersProcessor(Project project, MoveCallback moveCallback) {
    super(project);
    myMoveCallback = moveCallback;
  }

  public MoveMembersProcessor(Project project, MoveMembersOptions options) {
    super(project);
    setOptions(options);
  }

  protected String getCommandName() {
    return MoveMembersImpl.REFACTORING_NAME;
  }

  public void invoke(final MoveMembersDialog dialog) {
    setOptions(dialog);
    setPrepareSuccessfulSwingThreadCallback(new Runnable() {
      public void run() {
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    });
    run(null);
  }

  public void testRun(MoveMembersOptions dialog) {
    setOptions(dialog);
    super.testRun();
  }

  private void setOptions(MoveMembersOptions dialog) {
    myDialog = dialog;
    PsiMember[] members = myDialog.getSelectedMembers();
    myMembersToMove.clear();
    for (int idx = 0; idx < members.length; idx++) {
      myMembersToMove.add(members[idx]);
    }
    final PsiManager manager = PsiManager.getInstance(myProject);
    myTargetClass = manager.findClass(myDialog.getTargetClassName(), GlobalSearchScope.allScope(myProject));
    myNewVisibility = dialog.getMemberVisibility();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new MoveMemberViewDescriptor(myMembersToMove.toArray(new PsiElement[myMembersToMove.size()]), usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiSearchHelper helper = manager.getSearchHelper();
    final List<UsageInfo> usagesList = new ArrayList<UsageInfo>();
    for (Iterator<PsiMember> it = myMembersToMove.iterator(); it.hasNext();) {
      PsiMember member = it.next();
      PsiReference[] refs = helper.findReferences(member, GlobalSearchScope.projectScope(myProject), false);
      for (int i = 0; i < refs.length; i++) {
        PsiElement ref = refs[i].getElement();
        if (ref instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
          PsiExpression qualifier = refExpr.getQualifierExpression();
          if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, myMembersToMove, myTargetClass, true)) {
            // both member and the reference to it will be in target class
            if (!isInMovedElement(refExpr)) {
              if (qualifier != null) {
                usagesList.add(new MyUsageInfo(member, refExpr, null, qualifier));  // remove qualifier
              }
            } else {
              if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(member.getContainingClass())) {
                usagesList.add(new MyUsageInfo(member, refExpr, null, qualifier));  // change qualifier
              }
            }
          }
          else {
            // member in target class, the reference will be outside target class
            if (qualifier == null) {
              usagesList.add(new MyUsageInfo(member, refExpr, myTargetClass, refExpr)); // add qualifier
            }
            else {
              usagesList.add(new MyUsageInfo(member, refExpr, myTargetClass, qualifier)); // change qualifier
            }
          }
        }
        else {
          if (!isInMovedElement(ref)) {
            usagesList.add(new MyUsageInfo(member, ref, null, ref));
          }
        }
      }
    }
    UsageInfo[] usageInfos = usagesList.toArray(new UsageInfo[usagesList.size()]);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(myMembersToMove.size() == elements.length);
    myMembersToMove.clear();
    for (int idx = 0; idx < elements.length; idx++) {
      myMembersToMove.add((PsiMember)elements[idx]);
    }
  }

  private boolean isInMovedElement(PsiElement element) {
    PsiElement parent = element;
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      if (parent instanceof PsiMethod || parent instanceof PsiField || parent instanceof PsiClass) {
        if (myMembersToMove.contains(parent)) return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myDialog.isPreviewUsages();
    if (UsageViewUtil.hasReadOnlyUsages(usages)){
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
    }
    return toPreview;
  }

  protected void performRefactoring(final UsageInfo[] usages) {
    try {
      // correct references to moved members from the outside
      ArrayList<MyUsageInfo> otherUsages = new ArrayList<MyUsageInfo>();
      for (int idx = 0; idx < usages.length; idx++) {
        MyUsageInfo usage = (MyUsageInfo)usages[idx];
        if (!usage.reference.isValid()) continue;
        if (usage.reference instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression)usage.reference;
          PsiExpression qualifier = refExpr.getQualifierExpression();
          if (qualifier != null) {
            if (usage.qualifierClass != null) {
              changeQualifier(refExpr, usage.qualifierClass);
            }
            else {
              removeQualifier(refExpr);
            }
          }
          else { // no qualifier
            if (usage.qualifierClass != null) {
              addQualifier(refExpr, usage.qualifierClass);
            }
          }
        }
        else {
          otherUsages.add(usage);
        }
      }

      // correct references inside moved members and outer references to Inner Classes
      for (Iterator it = myMembersToMove.iterator(); it.hasNext();) {
        PsiElement member = (PsiElement)it.next();
        if (member instanceof PsiVariable) {
          ((PsiVariable) member).normalizeDeclaration();
        }
        final RefactoringElementListener elementListener = getTransaction().getElementListener(member);
        ChangeContextUtil.encodeContextInfo(member, true);
        final PsiElement memberCopy = member.copy();
        ArrayList<PsiReference> refsToBeRebind = new ArrayList<PsiReference>();
        for (Iterator<MyUsageInfo> iterator = otherUsages.iterator(); iterator.hasNext();) {
          MyUsageInfo info = iterator.next();
          if (member.equals(info.member)) {
            PsiReference ref = info.reference.getReference();
            if (ref != null) {
              refsToBeRebind.add(ref);
            }
            iterator.remove();
          }
        }
        member.delete();

        PsiElement newMember = myTargetClass.add(memberCopy);

        fixVisibility(newMember);
        for (int i = 0; i < refsToBeRebind.size(); i++) {
          PsiReference reference = refsToBeRebind.get(i);
          reference.bindToElement(newMember);
        }

        elementListener.elementMoved(newMember);
      }

      // qualifier info must be decoded after members are moved
      ChangeContextUtil.decodeContextInfo(myTargetClass, null, null);
      myMembersToMove.clear();
      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void fixVisibility(PsiElement newMember) throws IncorrectOperationException {
    PsiModifierList modifierList = ((PsiModifierListOwner) newMember).getModifierList();

    if(myTargetClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
      modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
      return;
    }

    if(myNewVisibility == null) return;

    RefactoringUtil.setVisibility(modifierList, myNewVisibility);
  }


  private void removeQualifier(PsiReferenceExpression refExpr) throws IncorrectOperationException{
    PsiIdentifier identifier = (PsiIdentifier)refExpr.getReferenceNameElement();
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    PsiExpression expr = factory.createExpressionFromText(identifier.getText(), null);
    refExpr.replace(expr);
  }

  private PsiReferenceExpression addQualifier(PsiReferenceExpression refExpr, PsiClass aClass) throws IncorrectOperationException{
    PsiIdentifier identifier = (PsiIdentifier)refExpr.getReferenceNameElement();
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    PsiReferenceExpression expr = (PsiReferenceExpression)factory.createExpressionFromText("q."+identifier.getText(), null);
    expr = (PsiReferenceExpression)CodeStyleManager.getInstance(myProject).reformat(expr);

    PsiReferenceExpression qualifier = factory.createReferenceExpression(aClass);
    expr.getQualifierExpression().replace(qualifier);

    if (refExpr.getParent() != null) {
      return (PsiReferenceExpression) refExpr.replace(expr);
    } else {
      return expr;
    }
  }

  private void changeQualifier(PsiReferenceExpression refExpr, PsiClass aClass) throws IncorrectOperationException{
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    PsiReferenceExpression qualifier = factory.createReferenceExpression(aClass);
    refExpr.getQualifierExpression().replace(qualifier);
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    final ArrayList<String> conflicts = new ArrayList<String>();
    for (Iterator<PsiMember> iterator = myMembersToMove.iterator(); iterator.hasNext();) {
      final PsiElement member = iterator.next();
      RefactoringUtil.analyzeModuleConflicts(myProject, Collections.singletonList(member), myTargetClass, conflicts);
    }
    return showConflicts(conflicts, usages);
  }

  public void run(Object markerId) {
    if (myMembersToMove.size() == 0){
      String message = "No members selected";
      RefactoringMessageUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, myProject);
      return;
    }
    if (canRefactor()) {
      super.run(markerId);
    }
  }

  private boolean canRefactor() {
    final String[] conflicts = analyzeMoveConflicts(myMembersToMove, myTargetClass, myNewVisibility);
    if (conflicts.length > 0) {
      ConflictsDialog dialog = new ConflictsDialog(conflicts, myProject);
      dialog.show();
      return dialog.isOK();
    }
    return true;
  }

  private static String[] analyzeMoveConflicts(final Set<PsiMember> membersToMove, final PsiClass targetClass, String newVisibility) {
    final LinkedHashSet<String> conflicts = new LinkedHashSet<String>();
    for (Iterator<PsiMember> iterator = membersToMove.iterator(); iterator.hasNext();) {
      final PsiMember element = iterator.next();
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        if (hasMethod(targetClass, method)) {
          String message = ConflictsUtil.getDescription(method, false) + " already exists in the target class.";
          message = ConflictsUtil.capitalize(message);
          conflicts.add(message);
        }
      }
      else if (element instanceof PsiField) {
        PsiField field = (PsiField)element;
        if (hasField(targetClass, field)) {
          String message = ConflictsUtil.getDescription(field, false) + " already exists in the target class.";
          message = ConflictsUtil.capitalize(message);
          conflicts.add(message);
        }
      }
    }
    return analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility);
  }

  public static String[] analyzeAccessibilityConflicts(final Set<PsiMember> membersToMove,
                                                        final PsiClass targetClass,
                                                        final LinkedHashSet<String> conflicts, String newVisibility) {
    for (Iterator<PsiMember> it = membersToMove.iterator(); it.hasNext();) {
      PsiMember member = it.next();


      checkUsedElements(member, member, membersToMove, targetClass, conflicts);

      PsiModifierList modifierList = (PsiModifierList)member.getModifierList().copy();

      if(newVisibility != null) {
        try {
          RefactoringUtil.setVisibility(modifierList, newVisibility);
        }
        catch(IncorrectOperationException ex) {
          /* do nothing and hope for the best */
        }
      }
      final boolean isMemberPublic =
              modifierList.hasModifierProperty(PsiModifier.PUBLIC)
              || targetClass.isInterface()
              || (newVisibility != null && PsiModifier.PUBLIC.equals(newVisibility));
      if (!isMemberPublic) {
        PsiManager manager = member.getManager();
        PsiSearchHelper helper = manager.getSearchHelper();
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
        PsiReference[] references = helper.findReferences(member, projectScope, false);
        for (int i = 0; i < references.length; i++) {
          PsiElement ref = references[i].getElement();
          if (!RefactoringHierarchyUtil.willBeInTargetClass(ref, membersToMove, targetClass, false)) {
            if (!manager.getResolveHelper().isAccessible(member, modifierList, ref, null)) {
              String message = ConflictsUtil.getDescription(member, true)
                + " is " + getVisiblityString(member)
                + " and will not be accessible from "
                + ConflictsUtil.getDescription(ConflictsUtil.getContainer(ref), true)
                + " when moved to the target class.";
              message = ConflictsUtil.capitalize(message);
              conflicts.add(message);
            }
          }
        }
      }
    }
    return conflicts.toArray(new String[conflicts.size()]);
  }

  private static void checkUsedElements(PsiMember member, PsiElement scope, Set<PsiMember> membersToMove, PsiClass newContext, LinkedHashSet<String> conflicts) {
    if(scope instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)){
          PsiExpression qualifier = refExpr.getQualifierExpression();
          PsiClass accessClass = (PsiClass) (qualifier != null ? PsiUtil.getAccessObjectClass(qualifier).getElement() : null);
          checkAccessibility(((PsiMember)refElement), newContext, accessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiNewExpression) {
      final PsiMethod refElement = ((PsiNewExpression)scope).resolveConstructor();
      if (refElement != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)) {
          checkAccessibility(refElement, newContext, null, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)){
          checkAccessibility(((PsiMember)refElement), newContext, null, member, conflicts);
        }
      }
    }

    PsiElement[] children = scope.getChildren();
    for (int idx = 0; idx < children.length; idx++) {
      PsiElement child = children[idx];
      if (!(child instanceof PsiWhiteSpace)) {
        checkUsedElements(member, child, membersToMove, newContext, conflicts);
      }
    }
  }

  private static void checkAccessibility(PsiMember refMember,
                                         PsiClass newContext,
                                         PsiClass accessClass,
                                         PsiMember member,
                                         LinkedHashSet<String> conflicts) {
    if (!PsiUtil.isAccessible(refMember, newContext, accessClass)) {
      String message = ConflictsUtil.getDescription(refMember, true)
        + " is " + getVisiblityString(refMember)
        + " and will not be accessible from "
        + ConflictsUtil.getDescription(member, false)
        + " in the target class.";
      message = ConflictsUtil.capitalize(message);
      conflicts.add(message);
    }
  }

  private static boolean hasMethod(PsiClass targetClass, PsiMethod method) {
    PsiMethod[] targetClassMethods = targetClass.getMethods();
    for (int i = 0; i < targetClassMethods.length; i++) {
      if (MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                 targetClassMethods[i].getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  private static String getVisiblityString(PsiMember member) {
    if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
      return "public";
    }
    if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
      return "protected";
    }
    if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      return "private";
    }
    return "package local";
  }

  private static boolean hasField(PsiClass targetClass, PsiField field) {
    String fieldName = field.getName();
    PsiField[] targetClassFields = targetClass.getFields();
    for (int i = 0; i < targetClassFields.length; i++) {
      PsiField targetClassField = targetClassFields[i];
      if (fieldName.equals(targetClassField.getName())) {
        return true;
      }
    }
    return false;
  }

  public List<PsiElement> getMembers() {
    return new ArrayList<PsiElement>(myMembersToMove);
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }


  private static class MyUsageInfo extends UsageInfo {
    public final PsiClass qualifierClass;
    public final PsiElement reference;
    public final PsiElement member;

    public MyUsageInfo(PsiElement member, PsiElement reference, PsiClass qualifierClass, PsiElement highlightElement) {
      super(highlightElement);
      this.member = member;
      this.qualifierClass = qualifierClass;
      this.reference = reference;
    }
  }

}
