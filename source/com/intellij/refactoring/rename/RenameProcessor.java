package com.intellij.refactoring.rename;

import com.intellij.ant.PsiAntElement;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUsagesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamer;
import com.intellij.refactoring.rename.naming.FormsRenamer;
import com.intellij.refactoring.rename.naming.InheritorRenamer;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;

import java.util.*;

public class RenameProcessor extends BaseRefactoringProcessor implements RenameDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameProcessor");

  protected ArrayList<PsiElement> myElements = new ArrayList<PsiElement>();
  protected ArrayList<String> myNames = new ArrayList<String>();

  private RenameDialog myDialog;
  private PsiElement myElement;
  private String myNewName = null;

  boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private boolean myPreviewUsages;
  private String myCommandName;
  private boolean myShouldRenameVariables;
  private boolean myShouldRenameInheritors;
  private boolean myShouldRenameForms;
  private UsageInfo[] myUsagesForNonCodeRenaming;
  private List<AutomaticRenamer<? extends PsiNamedElement>> myRenamers = new ArrayList<AutomaticRenamer<? extends PsiNamedElement>>();

  public RenameProcessor(Project project, PsiElement element, String newName,
                         boolean isSearchInComments, boolean toSearchInNonJavaFiles, boolean isPreviewUsages) {
    super(project);
    myDialog = null;
    myElement = element;

    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = toSearchInNonJavaFiles;
    myPreviewUsages = isPreviewUsages;

    setNewName(newName);
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(myElements);
  }


  public void setShouldRenameVariables(boolean shouldRenameVariables) {
    myShouldRenameVariables = shouldRenameVariables;
  }

  public void setShouldRenameInheritors(boolean shouldRenameInheritors) {
    myShouldRenameInheritors = shouldRenameInheritors;
  }



  public RenameProcessor(Project project, PsiElement element) {
    super(project);
    myElement = element;
  }

  public boolean isVariable() {
    return myElement instanceof PsiVariable;
  }

  public void run(Object markerId) {
    String message = null;
    prepareRenaming();
    try {
      for (int i = 0; i < myElements.size(); i++) {
        PsiElement element = myElements.get(i);
        String name = myNames.get(i);
        RenameUtil.checkRename(element, name);
      }
    } catch (IncorrectOperationException e) {
      message = e.getMessage();
    }

    if (message != null) {
      RefactoringMessageUtil.showErrorMessage("Rename", message, getHelpID(), myProject);
      return;
    }

    super.run(markerId);
  }

  public void prepareRenaming() {
    if (myElement instanceof PsiField) {
      prepareFieldRenaming((PsiField) myElement, myNewName);
    } else if (myElement instanceof PsiMethod) {
      prepareMethodRenaming((PsiMethod) myElement, myNewName);
    } else if (myElement instanceof PsiPackage) {
      preparePackageRenaming((PsiPackage) myElement, myNewName);
    }
  }

  private void preparePackageRenaming(PsiPackage psiPackage, String newName) {
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (!directory.isSourceRoot()) {
        myElements.add(directory);
        myNames.add(newName);
      }
    }
  }

  protected String getHelpID() {
    return HelpID.getRenameHelpID(myElement);
  }

  public void run(RenameDialog dialog) {
    myDialog = dialog;
    setNewName(dialog.getNewName());

    mySearchInComments = dialog.isSearchInComments();
    mySearchInNonJavaFiles = dialog.isSearchInNonJavaFiles();
    myPreviewUsages = dialog.isPreviewUsages();
    myShouldRenameVariables = dialog.shouldRenameVariables();
    myShouldRenameInheritors = dialog.shouldRenameInheritors();
    myShouldRenameForms = dialog.shouldRenameForms();

    run((Object) null);
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    if (myDialog != null) {
      String[] conflicts = RenameUtil.getConflictDescriptions(usages[0]);
      if (conflicts.length > 0) {
        ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts, myProject);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) {
          return false;
        }
      }
    }
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usages[0]));
    RenameUtil.removeConflictUsages(usagesSet);

    final List<UsageInfo> variableUsages = new ArrayList<UsageInfo>();
    if (!myRenamers.isEmpty()) {
      boolean isOK = findRenamedVariables(variableUsages);

      if (!isOK) return false;
    }

    if (!variableUsages.isEmpty()) {
      usagesSet.addAll(variableUsages);
      usages[0] = usagesSet.toArray(new UsageInfo[usagesSet.size()]);
    }

    if (myDialog != null) {
      // make sure that dialog is closed in swing thread
      ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
        public void run() {
          myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
      });
    }
    return true;
  }

  private boolean findRenamedVariables(final List<UsageInfo> variableUsages) {
    for (Iterator<AutomaticRenamer<? extends PsiNamedElement>> iterator = myRenamers.iterator(); iterator.hasNext();) {
      final AutomaticRenamer<? extends PsiNamedElement> automaticVariableRenamer = iterator.next();
      if (!automaticVariableRenamer.hasAnythingToRename()) continue;
      final AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, automaticVariableRenamer);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    for (Iterator<AutomaticRenamer<? extends PsiNamedElement>> iterator = myRenamers.iterator(); iterator.hasNext();) {
      final AutomaticRenamer<? extends PsiNamedElement> renamer = iterator.next();
      final List<? extends PsiNamedElement> variables = renamer.getElements();
      for (Iterator<? extends PsiNamedElement> iterator1 = variables.iterator(); iterator1.hasNext();) {
        final PsiNamedElement variable = iterator1.next();
        addElement(variable, renamer.getNewName(variable));
      }
    }

    Runnable runnable = new Runnable() {
      public void run() {
        for (Iterator<AutomaticRenamer<? extends PsiNamedElement>> iterator = myRenamers.iterator(); iterator.hasNext();) {
          final AutomaticRenamer<? extends PsiNamedElement> renamer = iterator.next();
          renamer.findUsages(variableUsages, mySearchInComments, mySearchInNonJavaFiles);
        }
      }
    };

    final boolean isOK = ApplicationManager.getApplication().runProcessWithProgressSynchronously(
          runnable, "Searching for variables", true, myProject
        );
    return isOK;
  }

  public void addElement(PsiElement element, String newName) {
    myElements.add(element);
    myNames.add(newName);
  }

  private void setNewName(String newName) {
    if (myElement == null) {
      myCommandName = "Renaming something";
      return;
    }
    int oldIndex = myElements.indexOf(myElement);
    if (oldIndex >= 0) {
      myElements.remove(oldIndex);
      myNames.remove(oldIndex);
    }

    myNewName = newName;
    myElements.add(myElement);
    myNames.add(newName);
    myCommandName = "Renaming " + UsageViewUtil.getType(myElement) + " " + UsageViewUtil.getDescriptiveName(myElement) + " to " + newName;
  }

  protected void prepareFieldRenaming(PsiField field, String newName) {
    // search for getters/setters
    PsiClass aClass = field.getContainingClass();

    final CodeStyleManager manager = CodeStyleManager.getInstance(myProject);

    final String propertyName =
        manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
    PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
      shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    }

    String newGetterName = "";
    String newSetterName = "";

    if (getter != null) {
      String getterId = getter.getName();
      newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
      if (newGetterName.equals(getterId)) {
        getter = null;
        newGetterName = null;
      }
    }

    if (setter != null) {
      newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      } else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null || setter != null) {
      if (askToRenameAccesors(getter, setter, newName)) {
        getter = null;
        setter = null;
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null) {
      addOverriddenAndImplemented(aClass, getter, newGetterName);
    }

    if (setter != null) {
      addOverriddenAndImplemented(aClass, setter, newSetterName);
    }

    if (shouldRenameSetterParameter) {
      PsiParameter parameter = setter.getParameterList().getParameters()[0];
      myElements.add(parameter);
      myNames.add(manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
    }
  }

  protected boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName) {
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, "Rename", getter, setter);
    return Messages.showYesNoDialog(myProject, text, "Rename", Messages.getQuestionIcon()) != 0;
  }

  private void addOverriddenAndImplemented(PsiClass aClass, PsiMethod methodPrototype, String newName) {
    final HashSet<PsiClass> superClasses = new HashSet<PsiClass>();
    RefactoringHierarchyUtil.getSuperClasses(aClass, superClasses, true);
    superClasses.add(aClass);

    for (Iterator<PsiClass> iterator = superClasses.iterator(); iterator.hasNext();) {
      PsiClass superClass = iterator.next();

      PsiMethod method = superClass.findMethodBySignature(methodPrototype, false);

      if (method != null) {
        myElements.add(method);
        myNames.add(newName);
      }
    }
  }


  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new RenameViewDescriptor(myElement, myElements, myNames, mySearchInComments, mySearchInNonJavaFiles, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    myRenamers.clear();
    if (myElement instanceof PsiDirectory) {
      final UsageInfo[] usages = RenameUtil.findUsages(((PsiDirectory) myElement).getPackage(), myNewName, mySearchInComments, mySearchInNonJavaFiles);
      return UsageViewUtil.removeDuplicatedUsages(usages);
    }

    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    for (int i = 0; i < myElements.size(); i++) {
      PsiElement element = myElements.get(i);
      final String newName = myNames.get(i);
      if (element instanceof PsiDirectory) continue;
      final UsageInfo[] usages = RenameUtil.findUsages(element, newName,
          mySearchInComments, mySearchInNonJavaFiles);
      result.addAll(Arrays.asList(usages));
      if (element instanceof PsiClass && myShouldRenameVariables) {
        myRenamers.add(new AutomaticVariableRenamer((PsiClass) element, newName, Arrays.asList(usages)));
      }
      if (element instanceof PsiClass && myShouldRenameInheritors) {
        if (((PsiClass)element).getName() != null) {
          myRenamers.add(new InheritorRenamer((PsiClass) element, newName));
        }
      }

      if (element instanceof PsiClass && myShouldRenameForms) {
        myRenamers.add(new FormsRenamer((PsiClass) element, newName));
      }
    }
    // add usages in ejb-jar.xml regardless of mySearchInNonJavaFiles setting
    // delete erroneous usages in ejb-jar.xml (e.g. belonging to another ejb)
    EjbUsagesUtil.adjustEjbUsages(myElements, myNames, result);

    if (myElement != null) {
      // add usages in ejb-jar.xml regardless of mySearchInNonJavaFiles setting
      // delete erroneous usages in ejb-jar.xml (e.g. belonging to another ejb)
      EjbUsagesUtil.adjustEjbUsages(myElement, myNewName, result);
    }

    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length > 0);
    if (myElement != null) {
      myElement = elements[0];
    }
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      myElements.set(i, element);
    }
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    boolean toPreview = myPreviewUsages;
    if (!isNonCodeElements() && UsageViewUtil.hasNonCodeUsages(usages)) {
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in comments, strings and non-java files");
    } else if (UsageViewUtil.hasReadOnlyUsages(usages)) {
      toPreview = true;
      WindowManager.getInstance().getStatusBar(myProject).setInfo("Occurrences found in read-only files");
    }
    return toPreview;
  }

  private boolean isNonCodeElements() {
    for (Iterator<PsiElement> iterator = myElements.iterator(); iterator.hasNext();) {
      PsiElement element = iterator.next();
      if (!(element instanceof PsiAntElement) && //TODO:find a better way
          !(element instanceof XmlElement)) return false;
    }
    return true;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    HashSet<XmlTag> xmlTagsSet = new HashSet<XmlTag>();
    ArrayList<UsageInfo> specialRenaming = findXmlTags(usages, xmlTagsSet);

    List<Pair<String, RefactoringElementListener>> listenersForPackages = new ArrayList<Pair<String,RefactoringElementListener>>();
    for (int i = 0; i < myElements.size(); i++) {
      PsiElement element = myElements.get(i);
      if (element instanceof XmlTag && xmlTagsSet.contains(element)) continue;
      String newName = myNames.get(i);

      final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
      RenameUtil.doRename(element, newName, extractUsagesForElement(element, usages), myProject, elementListener);
      if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage) element;
        final String newQualifiedName = RenameUtil.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
        listenersForPackages.add(Pair.create(newQualifiedName, elementListener));
      }
    }

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (int i = 0; i < listenersForPackages.size(); i++) {
      Pair<String, RefactoringElementListener> pair = listenersForPackages.get(i);
      final PsiPackage aPackage = psiManager.findPackage(pair.getFirst());
      LOG.assertTrue(aPackage != null);
      pair.getSecond().elementRenamed(aPackage);
    }

    final UsageInfo[] usagesForNonCodeRenaming;
    if (specialRenaming.isEmpty()) {
      usagesForNonCodeRenaming = usages;
    } else {
      specialRenaming.addAll(Arrays.asList(usages));
      usagesForNonCodeRenaming = specialRenaming.toArray(new UsageInfo[specialRenaming.size()]);
    }
    myUsagesForNonCodeRenaming = usagesForNonCodeRenaming;
  }

  protected void performPsiSpoilingRefactoring() {
    RefactoringUtil.renameNonCodeUsages(myProject, myUsagesForNonCodeRenaming);
  }

  private ArrayList<UsageInfo> findXmlTags(UsageInfo[] usages, HashSet<XmlTag> xmlTagsSet) {
    ArrayList<UsageInfo> specialRenaming = new ArrayList<UsageInfo>();
    for (int i = 0; i < myElements.size(); i++) {
      PsiElement element = myElements.get(i);
      if (element instanceof XmlTag) {
        final PsiFile containingFile = element.getContainingFile();
        usagesLoop:
        for (int j = 0; j < usages.length; j++) {
          UsageInfo usage = usages[j];
          if (usage.isNonCodeUsage) {
            final PsiFile usageFile = usage.getElement().getContainingFile();
            if (usageFile.getManager().areElementsEquivalent(usageFile, containingFile)) {
              xmlTagsSet.add((XmlTag)element);
              final XmlTag xmlTag = (XmlTag) element;
              final String newName = myNames.get(i);
              String newText =
                  "<" + xmlTag.getName() + ">" + newName + "</" + xmlTag.getName() + ">";
              TextRange range = xmlTag.getTextRange();
              specialRenaming.add(NonCodeUsageInfo.create(xmlTag.getContainingFile(), range.getStartOffset(), range.getEndOffset(), xmlTag, newText));
              break usagesLoop;
            }
          }
        }
      }
    }
    return specialRenaming;
  }


  protected String getCommandName() {
    return myCommandName;
  }

  private static UsageInfo[] extractUsagesForElement(PsiElement element, UsageInfo[] usages) {
    if (element instanceof PsiDirectory) {
      element = ((PsiDirectory) element).getPackage();
      LOG.assertTrue(element != null);
    }

    final ArrayList<UsageInfo> extractedUsages = new ArrayList<UsageInfo>(usages.length);
    for (int idx = 0; idx < usages.length; idx++) {
      UsageInfo usage = usages[idx];

      LOG.assertTrue(usage instanceof MoveRenameUsageInfo);

      MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo) usage;
      if (element.equals(usageInfo.referencedElement)) {
        extractedUsages.add(usageInfo);
      }
    }
    return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
  }

  protected void prepareMethodRenaming(PsiMethod method, String newName) {
    final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
    if (role == null) return;

    if (role instanceof EjbDeclMethodRole) {
      final PsiMethod[] implementations = EjbUtil.findEjbImplementations(method);
      if (implementations.length == 0) return;

      final String[] names = EjbDeclMethodRole.suggestImplNames(newName, role.getType(), role.getEjb());
      for (int i = 0; i < implementations.length; i++) {
        if (i < names.length && names[i] != null) {
          myElements.add(implementations[i]);
          myNames.add(names[i]);
        }
      }
    }
  }

  protected void prepareTestRun() {
    prepareRenaming();
  }

  public List<String> getNewNames() {
    return Collections.unmodifiableList(myNames);
  }

  public void setSearchInComments(boolean value) {
    mySearchInComments = value;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }


}
