package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.safeDelete.usageInfo.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author dsl
 */
public class SafeDeleteProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.SafeDeleteProcessor");
  private PsiElement[] myElements;
  private boolean mySearchInCommentsAndStrings;
  private boolean mySearchNonJava;
  private boolean myPreviewNonCodeUsages = true;

  private SafeDeleteProcessor(Project project, Runnable prepareSuccessfulCallback,
                             PsiElement[] elementsToDelete, boolean isSearchInComments, boolean isSearchNonJava) {
    super(project, prepareSuccessfulCallback);
    myElements = elementsToDelete;
    mySearchInCommentsAndStrings = isSearchInComments;
    mySearchNonJava = isSearchNonJava;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new SafeDeleteUsageViewDescriptor(usages, refreshCommand, myElements, this);
  }

  void setElements(PsiElement[] elements) {
    myElements = elements;
  }

  private boolean isInside(PsiElement place, PsiElement[] ancestors) {
    return isInside(place, Arrays.asList(ancestors));
  }
  private boolean isInside(PsiElement place, Collection<? extends PsiElement> ancestors) {
    for (Iterator<? extends PsiElement> iterator = ancestors.iterator(); iterator.hasNext();) {
      PsiElement element = iterator.next();
      if (PsiTreeUtil.isAncestor(element, place, false)) return true;
      if (place instanceof PsiComment && element instanceof PsiClass) {
        final PsiClass aClass = ((PsiClass)element);
        if (aClass.getParent() instanceof PsiJavaFile) {
          final PsiJavaFile file = ((PsiJavaFile)aClass.getParent());
          if (PsiTreeUtil.isAncestor(file, place, false)) {
            if (file.getClasses().length == 1) { // file will be deleted on class deletion
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    for (int i = 0; i < myElements.length; i++) {
      PsiElement element = myElements[i];
      if (element instanceof PsiClass) {
        findClassUsages(((PsiClass) element), usages);
      } else if (element instanceof PsiMethod) {
        findMethodUsages(((PsiMethod) element), usages);
      } else if (element instanceof PsiField) {
        findFieldUsages((PsiField) element, usages);
      }
    }
    final UsageInfo[] result = usages.toArray(new UsageInfo[usages.size()]);
    return UsageViewUtil.removeDuplicatedUsages(result);
  }

  protected boolean preprocessUsages(UsageInfo[][] u) {
    UsageInfo[] usages = u[0];
    final HashMap<PsiElement,UsageHolder> elementsToUsageHolders = sortUsages(usages);
    ArrayList<String> conflicts = new ArrayList<String>();

    for (int i = 0; i < myElements.length; i++) {
      PsiElement element = myElements[i];

      if(element instanceof PsiMethod) {
        final PsiClass containingClass = ((PsiMethod) element).getContainingClass();

        if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods((PsiMethod) element);
          for (int j = 0; j < superMethods.length; j++) {
            PsiMethod superMethod = superMethods[j];
            if (isInside(superMethod, myElements)) continue;
            if(superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
              String message = ConflictsUtil.getDescription(element, true) + " implements "
                      + ConflictsUtil.getDescription(superMethod, true) + ".";
              conflicts.add(message);
              break;
            }
          }
        }
      }

      UsageHolder usageHolder = elementsToUsageHolders.get(element);
      if (usageHolder != null) {

        if (usageHolder.getNonCodeUsagesNumber() != usageHolder.getNonSafeUsagesNumber()) {
          final String description = usageHolder.getDescription();
          if (description != null) {
            conflicts.add(description);
          }
        }
      }
    }
    if (conflicts.size() > 0) {
      UnsafeUsagesDialog dialog = new UnsafeUsagesDialog(conflicts.toArray(new String[conflicts.size()]), myProject);
      dialog.show();
      if (!dialog.isOK()) {
        final int exitCode = dialog.getExitCode();
        prepareSuccessful(); // dialog is always dismissed
        if (exitCode == UnsafeUsagesDialog.VIEW_USAGES_EXIT_CODE) {
          showUsages(usages);
        }
        return false;
      }
      else {
        myPreviewNonCodeUsages = false;
      }
    }

    final UsageInfo[] filteredUsages = filterAndQueryOverriding(usages);
    prepareSuccessful(); // dialog is always dismissed
    if(filteredUsages == null) {
      return false;
    }
    u[0] = filteredUsages;
    return true;
  }

  private void showUsages(final UsageInfo[] usages) {
    class Descriptor extends UsageViewDescriptorAdapter {
      Descriptor() {
        super(usages, null);
      }

      public PsiElement[] getElements() {
        return myElements;
      }

      public void refresh(PsiElement[] elements) {
        refreshUsages(elements);
      }

      public String getProcessedElementsHeader() {
        return "Attempting to delete";
      }

      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "References in code " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
      }

      public String getCommentReferencesText(int usagesCount, int filesCount) {
        return "Occurrences found in comments, strings and non-java files "
                + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "occurrence");
      }

      public boolean isSearchInText() {
        return mySearchInCommentsAndStrings || mySearchNonJava;
      }

      public boolean canRefresh() {
        return false;
      }

      public boolean isCancelInCommonGroup() {
        return true;
      }
    }
    final UsageView usageView = UsageViewManager.getInstance(myProject).addContent("Unsuccessful safe delete", new Descriptor(), false,
                                                                                   true, true, true, true);
    usageView.addDoProcessAction(new RerunSafeDelete(myProject, myElements, usageView), "Retry", null, "&Rerun Safe Delete");
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  private static class RerunSafeDelete implements Runnable {
    final SmartPsiElementPointer[] myPointers;
    private final Project myProject;
    private final UsageView myUsageView;

    RerunSafeDelete(Project project, PsiElement[] elements, UsageView usageView) {
      myProject = project;
      myUsageView = usageView;
      myPointers = new SmartPsiElementPointer[elements.length];
      for (int i = 0; i < elements.length; i++) {
        PsiElement element = elements[i];
        myPointers[i] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
      }
    }

    public void run() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            UsageViewManager.getInstance(myProject).closeContent(myUsageView);
            ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
            for (int i = 0; i < myPointers.length; i++) {
              SmartPsiElementPointer pointer = myPointers[i];
              final PsiElement element = pointer.getElement();
              if(element != null) {
                elements.add(element);
              }
            }
            if(elements.size() > 0) {
              new SafeDeleteHandler().invoke(myProject, elements.toArray(new PsiElement[elements.size()]), true);
            }
          }
        });
    }
  }

  private UsageInfo[] filterAndQueryOverriding(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    ArrayList<UsageInfo> overridingMethods = new ArrayList<UsageInfo>();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if(usage.isNonCodeUsage) {
        result.add(usage);
      } else if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
        overridingMethods.add(usage);
      } else {
        result.add(usage);
      }
    }

    if(overridingMethods.size() > 0) {
      OverridingMethodsDialog dialog = new OverridingMethodsDialog(myProject, overridingMethods);
      dialog.show();
      if(!dialog.isOK()) return null;
      result.addAll(dialog.getSelected());
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  /**
   * @param usages
   * @return Map from elements to UsageHolders
   */
  private HashMap<PsiElement,UsageHolder> sortUsages(UsageInfo[] usages) {
    HashMap<PsiElement,UsageHolder> result = new HashMap<PsiElement, UsageHolder>();

    for (int i = 0; i < usages.length; i++) {
      final UsageInfo usage = usages[i];

      if (usage instanceof SafeDeleteUsageInfo) {
        final PsiElement referencedElement = ((SafeDeleteUsageInfo) usage).getReferencedElement();
        if (!result.containsKey(referencedElement)) {
          result.put(referencedElement, new UsageHolder(referencedElement, usages));
        }
      }
    }
    return result;
  }


  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myElements.length);
    for (int i = 0; i < elements.length; i++) {
      myElements[i] = elements[i];
    }
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    if(myPreviewNonCodeUsages && UsageViewUtil.hasNonCodeUsages(usages)) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in comments, strings and non-java files");
      return true;
    }

    return super.isPreviewUsages(filterToBeDeleted(usages));
  }

  private UsageInfo[] filterToBeDeleted(UsageInfo[] infos) {
    ArrayList<UsageInfo> list = new ArrayList<UsageInfo>();
    for (int i = 0; i < infos.length; i++) {
      UsageInfo info = infos[i];
      if(!(info instanceof SafeDeleteReferenceUsageInfo) || (((SafeDeleteReferenceUsageInfo) info).isSafeDelete())) {
        list.add(info);
      }
    }
    return list.toArray(new UsageInfo[list.size()]);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        if (usage instanceof SafeDeleteReferenceUsageInfo && ((SafeDeleteReferenceUsageInfo) usage).isSafeDelete()) {
          ((SafeDeleteReferenceUsageInfo) usage).deleteElement();
        } else if (usage instanceof SafeDeletePrivatizeMethod) {
          ((SafeDeletePrivatizeMethod) usage).getMethod().getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        } else if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
          ((SafeDeleteOverridingMethodUsageInfo) usage).getOverridingMethod().delete();

        }
      }

      for (int i = 0; i < myElements.length; i++) {
        PsiElement element = myElements[i];
        if(element instanceof PsiVariable) {
          ((PsiVariable) element).normalizeDeclaration();
        }

        if (element instanceof PsiTypeParameter) {
            deleteTypeParameterExternalUsages((PsiTypeParameter)element);
        }
        element.delete();
      }
    } catch (IncorrectOperationException e) {
      RefactoringUtil.processIncorrectOperation(myProject, e);
    }
  }

  private void deleteTypeParameterExternalUsages(PsiTypeParameter typeParameter) throws IncorrectOperationException {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    int index = owner.getTypeParameterList().getTypeParameterIndex(typeParameter);

    PsiSearchHelper searchHelper = typeParameter.getManager().getSearchHelper();
    PsiReference[] ownerReferences = searchHelper.findReferences(owner, typeParameter.getResolveScope(), false);
    for (int j = 0; j < ownerReferences.length; j++) {
      PsiReference reference = ownerReferences[j];
      if (reference instanceof PsiJavaCodeReferenceElement) {
        PsiTypeElement[] typeArgs = ((PsiJavaCodeReferenceElement)reference).getParameterList().getTypeParameterElements();
        if (typeArgs.length > index) {
          typeArgs[index].delete();
        }
      }
    }
  }

  private String calcCommandName() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("Deleting ");
    return RefactoringUtil.calculatePsiElementDescriptionList(myElements, buffer);
  }

  private String myCachedCommandName = null;
  protected String getCommandName() {
    if (myCachedCommandName == null) {
      myCachedCommandName = calcCommandName();
    }
    return myCachedCommandName;
  }

  private void findClassUsages(final PsiClass psiClass, ArrayList<UsageInfo> usages) {
    PsiManager manager = psiClass.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    final PsiReference[] references = manager.getSearchHelper().findReferences(psiClass, projectScope, false);

    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      final PsiElement element = reference.getElement();

      if (!isInside(element, myElements)) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiImportStatement) {
          usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(parent, psiClass, true));
        } else {
          usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, psiClass, false));
        }
      }
    }

    addNonCodeUsages(psiClass, usages, myInsideDeletedElements);
  }

  private void findMethodUsages(PsiMethod psiMethod, ArrayList<UsageInfo> usages) {
    PsiManager manager = psiMethod.getManager();
    final PsiSearchHelper searchHelper = manager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    final PsiReference[] references = searchHelper.findReferences(psiMethod, projectScope, false);

    if(psiMethod.isConstructor()) {
      findConstructorUsages(psiMethod, references, usages);
      return;
    }
    final PsiMethod[] overridingMethods =
            removeDeletedMethods(searchHelper.findOverridingMethods(psiMethod, projectScope, true));

    boolean anyRefs = false;
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      final PsiElement element = reference.getElement();
      if (!isInside(element, myElements) && !isInside(element, overridingMethods)) {
        usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, psiMethod, false));
        anyRefs = true;
      }
    }

    final UsageInsideDeleted usageInsideDeleted;
    if (!anyRefs) {
      HashMap<PsiMethod,PsiReference[]> methodToReferences = new HashMap<PsiMethod, PsiReference[]>();
      for (int i = 0; i < overridingMethods.length; i++) {
        PsiMethod overridingMethod = overridingMethods[i];
        final PsiReference[] overridingReferences = searchHelper.findReferences(overridingMethod, projectScope, false);
        methodToReferences.put(overridingMethod, overridingReferences);
      }
      final HashSet<PsiMethod> validOverriding =
              validateOverridingMethods(psiMethod, references, Arrays.asList(overridingMethods), methodToReferences, usages);
      usageInsideDeleted = new UsageInsideDeleted() {
        public boolean isInsideDeleted(PsiElement usage) {
          if(usage instanceof PsiFile) return false;
          return isInside(usage, myElements) || isInside(usage,  validOverriding);
        }
      };
    }
    else {
      usageInsideDeleted = myInsideDeletedElements;
    }

    addNonCodeUsages(psiMethod, usages, usageInsideDeleted);
  }

  private PsiMethod[] removeDeletedMethods(PsiMethod[] methods) {
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      boolean isDeleted = false;
      for (int j = 0; j < myElements.length; j++) {
        PsiElement element = myElements[j];
        if(element == method) {
          isDeleted = true; break;
        }
      }
      if(!isDeleted) {
        list.add(method);
      }
    }
    return list.toArray(new PsiMethod[list.size()]);
  }

  private void findConstructorUsages(PsiMethod constructor, PsiReference[] originalReferences, ArrayList<UsageInfo> usages) {
    final PsiSearchHelper searchHelper = constructor.getManager().getSearchHelper();

    HashMap<PsiMethod,PsiReference[]> constructorsToRefs = new HashMap<PsiMethod, PsiReference[]>();
    HashSet<PsiMethod> newConstructors = new HashSet<PsiMethod>();
    newConstructors.add(constructor);
    constructorsToRefs.put(constructor, originalReferences);
    HashSet<PsiMethod> passConstructors = new HashSet<PsiMethod>();
    do {
      passConstructors.clear();
      for (Iterator<PsiMethod> iterator = newConstructors.iterator(); iterator.hasNext();) {
        PsiMethod method = iterator.next();
        final PsiReference[] references = constructorsToRefs.get(method);
        for (int i = 0; i < references.length; i++) {
          PsiReference reference = references[i];
          PsiMethod overridingConstructor = getOverridingConstructorOfSuperCall(reference.getElement());
          if(overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
            PsiReference[] overridingConstructorReferences =
                    searchHelper.findReferences(overridingConstructor, GlobalSearchScope.projectScope(myProject), false);
            constructorsToRefs.put(overridingConstructor, overridingConstructorReferences);
            passConstructors.add(overridingConstructor);
          }
        }
      }
      newConstructors.clear();
      newConstructors.addAll(passConstructors);
    }
    while(!newConstructors.isEmpty());

    final HashSet<PsiMethod> validOverriding =
            validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet(), constructorsToRefs, usages);

    addNonCodeUsages(constructor, usages, new UsageInsideDeleted() {
      public boolean isInsideDeleted(PsiElement usage) {
        if(usage instanceof PsiFile) return false;
        return isInside(usage, myElements) || isInside(usage, validOverriding);
      }
    });
  }

  private HashSet<PsiMethod> validateOverridingMethods(PsiMethod originalMethod, final PsiReference[] originalReferences,
                                         Collection<PsiMethod> overridingMethods, HashMap<PsiMethod,PsiReference[]> methodToReferences, ArrayList<UsageInfo> usages) {
    LinkedHashSet<PsiMethod> validOverriding = new LinkedHashSet<PsiMethod>(overridingMethods);
    boolean anyNewBadRefs;
    do {
      anyNewBadRefs = false;
      for (Iterator<PsiMethod> iterator = overridingMethods.iterator(); iterator.hasNext();) {
        PsiMethod overridingMethod = iterator.next();

        if (validOverriding.contains(overridingMethod)) {
          final PsiReference[] overridingReferences = methodToReferences.get(overridingMethod);
          boolean anyOverridingRefs = false;
          for (int j = 0; j < overridingReferences.length && !anyOverridingRefs; j++) {
            final PsiElement element = overridingReferences[j].getElement();
            if (!isInside(element, myElements) && !isInside(element, validOverriding)) {
              anyOverridingRefs = true;
            }
          }

          if (anyOverridingRefs) {
            validOverriding.remove(overridingMethod);
            anyNewBadRefs = true;

            for (int j = 0; j < originalReferences.length; j++) {
              PsiReference reference = originalReferences[j];
              final PsiElement element = reference.getElement();
              if (!isInside(element, myElements) && !isInside(element, overridingMethods)) {
                usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(element, originalMethod, false));
                validOverriding.clear();
              }
            }
          }
        }
      }
    }
    while(anyNewBadRefs && !validOverriding.isEmpty());

    for (Iterator<PsiMethod> iterator = validOverriding.iterator(); iterator.hasNext();) {
      PsiMethod method = iterator.next();
      if (method != originalMethod) {

        usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod));
      }
    }

    for (Iterator<PsiMethod> iterator = overridingMethods.iterator(); iterator.hasNext();) {
      PsiMethod method = iterator.next();
      if(!validOverriding.contains(method)) {
        final boolean methodCanBePrivate =
                canBePrivate(method, methodToReferences.get(method), validOverriding);
        if (methodCanBePrivate) {
          usages.add(new SafeDeletePrivatizeMethod(method, originalMethod));
        }
      }
    }
    return validOverriding;
  }

  private static PsiMethod getOverridingConstructorOfSuperCall(final PsiElement element) {
    PsiMethod overridingConstructor = null;
    if(element instanceof PsiReferenceExpression && "super".equals(element.getText())) {
      PsiElement parent = element.getParent();
      if(parent instanceof PsiMethodCallExpression) {
        parent = parent.getParent();
        if(parent instanceof PsiExpressionStatement) {
          parent = parent.getParent();
          if(parent instanceof PsiCodeBlock) {
            parent = parent.getParent();
            if(parent instanceof PsiMethod && ((PsiMethod) parent).isConstructor()) {
              overridingConstructor = (PsiMethod) parent;
            }
          }
        }
      }
    }
    return overridingConstructor;
  }

  private boolean canBePrivate(PsiMethod method, PsiReference[] references, Collection<? extends PsiElement> deleted) {
    final PsiClass containingClass = method.getContainingClass();
    if(containingClass == null) {
      return false;
    }

    PsiManager manager = method.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    final PsiModifierList privateModifierList;
    try {
      final PsiMethod newMethod = factory.createMethod("x3", PsiType.VOID);
      privateModifierList = newMethod.getModifierList();
      privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
      return false;
    }
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      final PsiElement element = reference.getElement();
      if(!isInside(element, myElements) && !isInside(element, deleted)
              && !manager.getResolveHelper().isAccessible(method, privateModifierList, element, null)) {
        return false;
      }
    }
    return true;
  }

  private void findFieldUsages(PsiField psiField, ArrayList<UsageInfo> usages) {
    PsiManager manager = psiField.getManager();
    final PsiReference[] references = manager.getSearchHelper().findReferences(psiField, GlobalSearchScope.projectScope(myProject), false);

    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      if (!myInsideDeletedElements.isInsideDeleted(reference.getElement())) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiAssignmentExpression && element == ((PsiAssignmentExpression) parent).getLExpression()) {
          usages.add(new SafeDeleteFieldWriteReference(((PsiAssignmentExpression) parent), psiField));
        } else {
          usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(reference.getElement(), psiField, false));
        }
      }

    }

    addNonCodeUsages(psiField, usages, myInsideDeletedElements);
  }


  private interface UsageInsideDeleted {
    boolean isInsideDeleted(PsiElement usage);
  }

  private final UsageInsideDeleted myInsideDeletedElements = new UsageInsideDeleted() {
    public boolean isInsideDeleted(PsiElement usage) {
      if(usage instanceof PsiFile) return false;
      return isInside(usage, myElements);
    }
  };

  private void addNonCodeUsages(final PsiElement element, ArrayList<UsageInfo> usages, final UsageInsideDeleted insideElements) {
    RefactoringUtil.UsageInfoFactory nonCodeUsageFactory = new RefactoringUtil.UsageInfoFactory() {
      public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
        if (!insideElements.isInsideDeleted(usage)) {
          return new SafeDeleteReferenceSimpleDeleteUsageInfo(usage, element, startOffset, endOffset, true, false);
        } else {
          return null;
        }
      }
    };
    if (mySearchInCommentsAndStrings) {
      String stringToSearch = RefactoringUtil.getStringToSearch(element, false);
      if (stringToSearch != null) {
        RefactoringUtil.addUsagesInStringsAndComments(element, stringToSearch, usages, nonCodeUsageFactory);
      }
    }
    if (mySearchNonJava && (element instanceof PsiClass || element instanceof PsiPackage)) {
      String stringToSearch = RefactoringUtil.getStringToSearch(element, true);
      if (stringToSearch != null) {
        RefactoringUtil.addUsagesInNonJavaFiles(element, stringToSearch, GlobalSearchScope.projectScope(myProject), usages, nonCodeUsageFactory);
      }
    }
  }

  public static boolean validElement(PsiElement element) {
    return element instanceof PsiClass
            || element instanceof PsiMethod
            || element instanceof PsiField;
  }

  public static SafeDeleteProcessor createInstance(Project project, Runnable prepareSuccessfulCallback,
                                                                   PsiElement[] elementsToDelete, boolean isSearchInComments, boolean isSearchNonJava) {
    return new SafeDeleteProcessor(project, prepareSuccessfulCallback, elementsToDelete, isSearchInComments, isSearchNonJava);
  }

  public static SafeDeleteProcessor createInstance(Project project, Runnable prepareSuccessfulCallBack,
                                                         PsiElement[] elementsToDelete, boolean isSearchInComments, boolean isSearchNonJava,
                                                         boolean askForAccessors) {
    PsiManager manager = PsiManager.getInstance(project);
    ArrayList<PsiElement> elements = new ArrayList<PsiElement>(Arrays.asList(elementsToDelete));
    HashSet<PsiElement> elementsToDeleteSet = new HashSet<PsiElement>(Arrays.asList(elementsToDelete));

    for (int i = 0; i < elementsToDelete.length; i++) {
      PsiElement psiElement = elementsToDelete[i];
      if (psiElement instanceof PsiField) {
        PsiField field = (PsiField) psiElement;
        final String propertyName =
                manager.getCodeStyleManager().variableNameToPropertyName(field.getName(), VariableKind.FIELD);

        PsiClass aClass = field.getContainingClass();
        if (aClass != null) {
          boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
          PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
          if (elementsToDeleteSet.contains(getter)) getter = null;
          PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);
          if (elementsToDeleteSet.contains(setter)) setter = null;
          if (askForAccessors && (getter != null || setter != null)) {
            final String message = RefactoringMessageUtil.getGetterSetterMessage(field.getName(), "Delete", getter, setter);
            if (Messages.showYesNoDialog(project, message, "Safe Delete", Messages.getQuestionIcon()) != 0) {
              getter = null;
              setter = null;
            }
          }
          if (setter != null) elements.add(setter);
          if (getter != null) elements.add(getter);
        }
      }
    }

    return new SafeDeleteProcessor(project, prepareSuccessfulCallBack,
                                   elements.toArray(new PsiElement[elements.size()]),
            isSearchInComments, isSearchNonJava);
  }

  public boolean isSearchInCommentsAndStrings() {
    return mySearchInCommentsAndStrings;
  }

  public void setSearchInCommentsAndStrings(boolean searchInCommentsAndStrings) {
    mySearchInCommentsAndStrings = searchInCommentsAndStrings;
  }

  public boolean isSearchNonJava() {
    return mySearchNonJava;
  }

  public void setSearchNonJava(boolean searchNonJava) {
    mySearchNonJava = searchNonJava;
  }


}
