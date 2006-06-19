package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import static com.intellij.find.findUsages.FindUsagesManager.FileSearchScope.*;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FindUsagesManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findParameterUsages.FindUsagesManager");

  enum FileSearchScope {
    FROM_START,
    FROM_END,
    AFTER_CARET,
    BEFORE_CARET
  }

  private static Key<String> KEY_START_USAGE_AGAIN = Key.create("KEY_START_USAGE_AGAIN");
  @NonNls private static final String VALUE_START_USAGE_AGAIN = "START_AGAIN";
  private Project myProject;
  private com.intellij.usages.UsageViewManager myAnotherManager;
  private boolean myToOpenInNewTab = false;
  private FindUsagesOptions myFindPackageOptions;
  private FindUsagesOptions myFindClassOptions;
  private FindUsagesOptions myFindMethodOptions;
  private FindUsagesOptions myFindVariableOptions;
  private FindUsagesOptions myFindPointcutOptions;

  private class LastSearchData {
    public SmartPsiElementPointer[] myLastSearchElements = null;
    public FindUsagesOptions myLastOptions = null;
  }

  private LastSearchData myLastSearchData = new LastSearchData();

  public FindUsagesManager(Project project, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;

    myFindPackageOptions = createFindUsagesOptions(project);
    myFindClassOptions = createFindUsagesOptions(project);
    myFindMethodOptions = createFindUsagesOptions(project);
    myFindVariableOptions = createFindUsagesOptions(project);
    myFindPointcutOptions = createFindUsagesOptions(project);
  }

  public static FindUsagesProvider getFindHandler(PsiElement elt) {
    return elt.getLanguage().getFindUsagesProvider();
  }

  public FindUsagesOptions getFindClassOptions() {
    return myFindClassOptions;
  }

  private FindUsagesOptions createFindUsagesOptions(Project project) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isIncludeOverloadUsages = false;
    findUsagesOptions.isIncludeSubpackages = true;
    findUsagesOptions.isReadAccess = true;
    findUsagesOptions.isWriteAccess = true;
    findUsagesOptions.isCheckDeepInheritance = true;
    findUsagesOptions.isSearchForTextOccurences = true;
    return findUsagesOptions;
  }

  public boolean canFindUsages(final PsiElement element) {
    if (element == null) return false;
    if (element instanceof PsiFile) return ((PsiFile)element).getVirtualFile() != null;
    final Language language = element.getLanguage();
    final FindUsagesProvider provider = language.getFindUsagesProvider();
    return provider.canFindUsagesFor(element);
  }

  public void clearFindingNextUsageInFile() {
    myLastSearchData.myLastOptions = null;
    myLastSearchData.myLastSearchElements = null;
  }

  public boolean findNextUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, AFTER_CARET);
  }

  public boolean findPreviousUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, BEFORE_CARET);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myToOpenInNewTab = JDOMExternalizer.readBoolean(element, "OPEN_NEW_TAB");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element, "OPEN_NEW_TAB", myToOpenInNewTab);
  }

  private boolean findUsageInFile(FileEditor editor, FileSearchScope direction) {
    if (editor == null) {
      throw new IllegalArgumentException("editor cannot be null");
    }

    SmartPsiElementPointer[] lastSearchElements = myLastSearchData.myLastSearchElements;
    if (lastSearchElements == null) return false;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (SmartPsiElementPointer pointer : lastSearchElements) {
      PsiElement element = pointer.getElement();
      if (element != null) elements.add(element);
    }
    if (elements.size() == 0) {
      Messages.showMessageDialog(myProject, FindBundle.message("find.searched.elements.have.been.changed.error"),
                                 FindBundle.message("cannot.search.for.usages.title"), Messages.getInformationIcon());
      // SCR #10022
      //clearFindingNextUsageInFile();
      return true;
    }

    //todo
    TextEditor textEditor = (TextEditor)editor;
    Document document = textEditor.getEditor().getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return false;


    PsiElement[] allElements = elements.toArray(new PsiElement[elements.size()]);
    FindUsagesOptions options = myLastSearchData.myLastOptions;


    findUsagesInEditor(new UsageInfoToUsageConverter.TargetElementsDescriptor(allElements), psiFile, direction, options, textEditor);
    return true;
  }


  private void initLastSearchElement(final FindUsagesOptions findUsagesOptions,
                                     UsageInfoToUsageConverter.TargetElementsDescriptor descriptor) {
    final PsiElement[] primaryElements = descriptor.getPrimaryElements();
    final PsiElement[] additionalElements = descriptor.getAdditionalElements();
    List<PsiElement> allElements = new ArrayList<PsiElement>(primaryElements.length + additionalElements.length);
    allElements.addAll(Arrays.asList(primaryElements));
    allElements.addAll(Arrays.asList(additionalElements));

    myLastSearchData.myLastSearchElements = new SmartPsiElementPointer[allElements.size()];
    int idx = 0;
    for (PsiElement psiElement : allElements) {
      myLastSearchData.myLastSearchElements[idx++] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiElement);
    }
    myLastSearchData.myLastOptions = findUsagesOptions;
  }

  public final boolean showDialog(AbstractFindUsagesDialog dialog) {
    if (dialog == null) {
      return false;
    }
    dialog.show();
    if (!dialog.isOK()) {
      return false;
    }

    setOpenInNewTab(dialog.isShowInSeparateWindow());
    return true;
  }

  public void findUsages(PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor) {
    PsiElement[] elementsToSearch = null;
    PsiElement[] secondaryElementsToSearch = null;
    if (!canFindUsages(psiElement)) {
      return;
    }

    if (psiElement instanceof PsiDirectory) {
      PsiPackage psiPackage = ((PsiDirectory)psiElement).getPackage();
      if (psiPackage == null) {
        return;
      }
      psiElement = psiPackage;
    }

    final String actionString = FindBundle.message("find.super.method.warning.action.verb");
    if (psiElement instanceof PsiMethod) {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)psiElement, actionString);
      if (methods.length > 1) {
        elementsToSearch = methods;
      }
      else if (methods.length == 1) {
        psiElement = methods[0];
      }
      else {
        return;
      }
    }

    if (psiElement == null) {
      return;
    }

    final FindUsagesDialog dialog = getFindUsagesDialog(psiElement, scopeFile != null);
    if (!showDialog(dialog)) return;

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();

    clearFindingNextUsageInFile();
    LOG.assertTrue(psiElement.isValid());
    if (psiElement instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)psiElement;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverriden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overriden
          if (aClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT) ||
              shouldSearchForParameterInOverridingMethods(psiElement, parameter)) {
            elementsToSearch = getParameterElementsToSearch(parameter);
          }
        }
      }
    }
    else if (psiElement instanceof PsiField) {
      final PsiField field = (PsiField)psiElement;
      if (field.getContainingClass() != null) {
        final String propertyName =
          field.getManager().getCodeStyleManager().variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        PsiMethod getter = PropertyUtil.
          findPropertyGetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(field.getContainingClass().getMethods()));
        PsiMethod setter = PropertyUtil.
          findPropertySetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(field.getContainingClass().getMethods()));
        if (getter != null || setter != null) {
          if (Messages.showDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                  FindBundle.message("find.field.accessors.title"),
                                  new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                                  Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
            final List<PsiElement> elements = new ArrayList<PsiElement>();
            if (getter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(getter, actionString)));
            }
            if (setter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(setter, actionString)));
            }
            secondaryElementsToSearch = elements.toArray(new PsiElement[elements.size()]);
          }
        }
      }
    }

    if (elementsToSearch == null) {
      elementsToSearch = new PsiElement[]{psiElement};
    }

    final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor =
      new UsageInfoToUsageConverter.TargetElementsDescriptor(elementsToSearch, secondaryElementsToSearch);
    if (scopeFile == null) {
      findUsages(descriptor, dialog.isSkipResultsWhenOneUsage(), dialog.isShowInSeparateWindow(), findUsagesOptions);
    }
    else {
      findUsagesOptions = (FindUsagesOptions)findUsagesOptions.clone();
      findUsagesOptions.isDerivedClasses = false;
      findUsagesOptions.isDerivedInterfaces = false;
      findUsagesOptions.isImplementingClasses = false;
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(descriptor, scopeFile, FROM_START, findUsagesOptions, editor);
    }
  }

  private static boolean shouldSearchForParameterInOverridingMethods(final PsiElement psiElement, final PsiParameter parameter) {
    return Messages.showDialog(psiElement.getProject(),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                               Messages.getQuestionIcon()) == 0;
  }

  private void setOpenInNewTab(final boolean toOpenInNewTab) {
    if (!mustOpenInNewTab()) {
      myToOpenInNewTab = toOpenInNewTab;
    }
  }

  public final boolean shouldOpenInNewTab() {
    return mustOpenInNewTab() || UsageViewManager.getInstance(myProject).getReusableContentsCount() == 0 || myToOpenInNewTab;
  }

  public final boolean mustOpenInNewTab() {
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    return selectedContent != null && selectedContent.isPinned();
  }

  private PsiElement[] getParameterElementsToSearch(final PsiParameter parameter) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiSearchHelper helper = parameter.getManager().getSearchHelper();
    PsiMethod[] overrides = helper.findOverridingMethods(method, GlobalSearchScope.allScope(myProject), true);
    for (int i = 0; i < overrides.length; i++) {
      overrides[i] = (PsiMethod)overrides[i].getNavigationElement();
    }
    PsiElement[] elementsToSearch = new PsiElement[overrides.length + 1];
    elementsToSearch[0] = parameter;
    int idx = method.getParameterList().getParameterIndex(parameter);
    for (int i = 0; i < overrides.length; i++) {
      elementsToSearch[i + 1] = overrides[i].getParameterList().getParameters()[idx];
    }
    return elementsToSearch;
  }

  private static UsageSearcher createUsageSearcher(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                            final FindUsagesOptions options,
                                            final PsiFile scopeFile) {
    return new UsageSearcher() {
      public void generate(final Processor<Usage> processor) {
        if (scopeFile != null) {
          options.searchScope = new LocalSearchScope(scopeFile);
        }
        final Processor<UsageInfo> usageInfoProcessorToUsageProcessorAdapter = new Processor<UsageInfo>() {
          public boolean process(UsageInfo usageInfo) {
            return processor.process(UsageInfoToUsageConverter.convert(descriptor, usageInfo));
          }
        };
        for (PsiElement primaryElement : descriptor.getPrimaryElements()) {
          LOG.assertTrue(primaryElement.isValid());
          FindUsagesUtil.processUsages(primaryElement, usageInfoProcessorToUsageProcessorAdapter, options);
        }

        for (PsiElement additionalElement : descriptor.getAdditionalElements()) {
          LOG.assertTrue(additionalElement.isValid());
          FindUsagesUtil.processUsages(additionalElement, usageInfoProcessorToUsageProcessorAdapter, options);
        }
      }
    };
  }

  private PsiElement2UsageTargetAdapter[] convertToUsageTargets(final PsiElement[] elementsToSearch) {
    final ArrayList<PsiElement2UsageTargetAdapter> targets = new ArrayList<PsiElement2UsageTargetAdapter>();
    if (elementsToSearch != null) {
      for (PsiElement element : elementsToSearch) {
        convertToUsageTarget(targets, element);
      }
    }
    return targets.toArray(new PsiElement2UsageTargetAdapter[targets.size()]);
  }

  private void findUsages(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                          final boolean toSkipUsagePanelWhenOneUsage,
                          final boolean toOpenInNewTab,
                          final FindUsagesOptions findUsagesOptions) {

    final PsiElement[] primaryElements = descriptor.getPrimaryElements();
    final PsiElement[] additionalElements = descriptor.getAdditionalElements();

    final UsageTarget[] targets = ArrayUtil
      .mergeArrays(convertToUsageTargets(primaryElements), convertToUsageTargets(additionalElements), UsageTarget.class);
    myAnotherManager.searchAndShowUsages(targets, new Factory<UsageSearcher>() {
      public UsageSearcher create() {
        return createUsageSearcher(descriptor, findUsagesOptions, null);
      }
    }, !toSkipUsagePanelWhenOneUsage, true, createPresentation(primaryElements[0], findUsagesOptions, toOpenInNewTab), null);
  }

  private UsageViewPresentation createPresentation(PsiElement psiElement,
                                                   final FindUsagesOptions findUsagesOptions,
                                                   boolean toOpenInNewTab) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = findUsagesOptions.searchScope != null ? findUsagesOptions.searchScope.getDisplayName() : null;
    presentation.setScopeText(scopeString);
    String usagesString = generateUsagesString(findUsagesOptions);
    presentation.setUsagesString(usagesString);
    String title;
    if (scopeString != null) {
      title = FindBundle
        .message("find.usages.of.element.in.scope.panel.title", usagesString, UsageViewUtil.getShortName(psiElement), scopeString);
    }
    else {
      title = FindBundle.message("find.usages.of.element.panel.title", usagesString, UsageViewUtil.getShortName(psiElement));
    }
    presentation.setTabText(title);
    presentation.setTargetsNodeText(UsageViewUtil.capitalize(UsageViewUtil.getType(psiElement)));
    presentation.setOpenInNewTab(toOpenInNewTab);
    return presentation;
  }

  private void findUsagesInEditor(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                  final PsiFile scopeFile,
                                  final FileSearchScope direction,
                                  final FindUsagesOptions findUsagesOptions,
                                  FileEditor fileEditor) {
    LOG.assertTrue(fileEditor != null);
    initLastSearchElement(findUsagesOptions, descriptor);

    clearStatusBar();

    final FileEditorLocation currentLocation = fileEditor.getCurrentLocation();

    final UsageSearcher usageSearcher = createUsageSearcher(descriptor, findUsagesOptions, scopeFile);
    final boolean[] usagesWereFound = new boolean[]{false};

    Usage fUsage = findSiblingUsage(usageSearcher, direction, currentLocation, usagesWereFound, fileEditor);

    if (fUsage != null) {
      fUsage.navigate(true);
      fUsage.selectInEditor();
    }
    else if (!usagesWereFound[0]) {
      String message = getNoUsagesFoundMessage(descriptor.getPrimaryElements()[0]);
      showHintOrStatusBarMessage(message, fileEditor);
    }
    else {
      fileEditor.putUserData(KEY_START_USAGE_AGAIN, VALUE_START_USAGE_AGAIN);
      showHintOrStatusBarMessage(getSearchAgainMessage(descriptor.getPrimaryElements()[0], direction), fileEditor);
    }
  }

  private String getNoUsagesFoundMessage(PsiElement psiElement) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    return FindBundle.message("find.usages.of.element_type.element_name.not.found.message", elementType, elementName);
  }

  private void clearStatusBar() {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    statusBar.setInfo("");
  }

  private String getSearchAgainMessage(PsiElement element, final FileSearchScope direction) {
    String message = getNoUsagesFoundMessage(element);
    if (direction == AFTER_CARET) {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
      String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
      if (shortcutsText.length() > 0) {
        message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
      }
      else {
        message = FindBundle.message("find.search.again.from.top.action.message", message);
      }
    }
    else {
      String shortcutsText =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS));
      if (shortcutsText.length() > 0) {
        message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
      }
      else {
        message = FindBundle.message("find.search.again.from.bottom.action.message", message);
      }
    }
    return message;
  }

  private void showHintOrStatusBarMessage(String message, FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      showEditorHint(message, textEditor.getEditor());
    }
    else {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      statusBar.setInfo(message);
    }
  }

  private Usage findSiblingUsage(final UsageSearcher usageSearcher,
                                 FileSearchScope dir,
                                 final FileEditorLocation currentLocation,
                                 final boolean[] usagesWereFound,
                                 FileEditor fileEditor) {
    final Usage[] foundUsage = new Usage[]{null};

    if (fileEditor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      if (dir == AFTER_CARET) {
        dir = FROM_START;
      }
      else {
        dir = FROM_END;
      }
    }

    final FileSearchScope direction = dir;


    usageSearcher.generate(new Processor<Usage>() {
      public boolean process(Usage usage) {
        usagesWereFound[0] = true;

        if (direction == FROM_START) {
          foundUsage[0] = usage;
          return false;
        }
        else if (direction == FROM_END) {
          foundUsage[0] = usage;
        }
        else if (direction == AFTER_CARET) {
          if (usage.getLocation().compareTo(currentLocation) > 0) {
            foundUsage[0] = usage;
            return false;
          }
        }
        else if (direction == BEFORE_CARET) {
          if (usage.getLocation().compareTo(currentLocation) < 0) {
            if (foundUsage[0] != null) {
              if (foundUsage[0].getLocation().compareTo(usage.getLocation()) < 0) {
                foundUsage[0] = usage;
              }
            }
            else {
              foundUsage[0] = usage;
            }
          }
          else {
            return false;
          }
        }

        return true;
      }
    });

    fileEditor.putUserData(KEY_START_USAGE_AGAIN, null);

    return foundUsage[0];
  }

  private void convertToUsageTarget(final List<PsiElement2UsageTargetAdapter> targets, PsiElement elementToSearch) {
    if (elementToSearch instanceof NavigationItem) {
      targets.add(new PsiElement2UsageTargetAdapter(elementToSearch));
    }
    else {
      throw new IllegalArgumentException("Wrong usage target:" + elementToSearch);
    }
  }

  private static String generateUsagesString(final FindUsagesOptions selectedOptions) {
    String usagesString = "";
    String suffix = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    ArrayList<String> strings = new ArrayList<String>();
    if ((selectedOptions.isUsages && selectedOptions.isUsages) || (selectedOptions.isClassesUsages && selectedOptions.isClassesUsages) ||
        (selectedOptions.isMethodsUsages && selectedOptions.isMethodsUsages) ||
        (selectedOptions.isFieldsUsages && selectedOptions.isFieldsUsages)) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    if (selectedOptions.isIncludeOverloadUsages && selectedOptions.isIncludeOverloadUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.overloaded.methods.usages"));
    }
    if ((selectedOptions.isDerivedClasses && selectedOptions.isDerivedClasses)) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.classes"));
    }
    if ((selectedOptions.isDerivedInterfaces && selectedOptions.isDerivedInterfaces)) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.interfaces"));
    }
    if ((selectedOptions.isImplementingClasses && selectedOptions.isImplementingClasses)) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.classes"));
    }
    if ((selectedOptions.isImplementingMethods && selectedOptions.isImplementingMethods)) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.methods"));
    }
    if ((selectedOptions.isOverridingMethods && selectedOptions.isOverridingMethods)) {
      strings.add(FindBundle.message("find.usages.panel.title.overriding.methods"));
    }
    if (strings.size() == 0) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    for (int i = 0; i < strings.size(); i++) {
      String s = strings.get(i);
      usagesString += (i == strings.size() - 1) ? s : s + suffix;
    }
    return usagesString;
  }

  private void showEditorHint(String message, final Editor editor) {
    HintManager hintManager = HintManager.getInstance();
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    hintManager.showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
  }

  public static String getHelpID(PsiElement element) {
    return element.getLanguage().getFindUsagesProvider().getHelpId(element);
  }

  private FindUsagesDialog getFindUsagesDialog(PsiElement element,
                                               boolean isSingleFile) {
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, myProject, myFindPackageOptions, this, isSingleFile);
    }
    else if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, myProject, myFindClassOptions, this, isSingleFile);
    }
    else if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, myProject, myFindMethodOptions, this, isSingleFile);
    }
    else if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, myProject, myFindVariableOptions, this, isSingleFile);
    }
    else if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, myProject, myFindPointcutOptions, this, isSingleFile);
    }
    else {
      FindUsagesProvider handler = getFindHandler(element);
      if (handler != null) {
        return new CommonFindUsagesDialog(element, myProject, createFindUsagesOptions(myProject), this, isSingleFile);
      }
      return null;
    }
  }

}