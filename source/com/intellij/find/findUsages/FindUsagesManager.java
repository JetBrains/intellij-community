package com.intellij.find.findUsages;

import com.intellij.aspects.psi.PsiPointcut;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.impl.HelpID;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.content.Content;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FindUsagesManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findParameterUsages.FindUsagesManager");

  private static HashMap<FileType, FindUsagesHandler> findHandlers = new HashMap<FileType, FindUsagesHandler>();

  public static final int FROM_START = 0;
  public static final int FROM_END = 3;
  public static final int AFTER_CARET = 1;
  public static final int BEFORE_CARET = 2;

  private static Key<String> KEY_START_USAGE_AGAIN = Key.create("KEY_START_USAGE_AGAIN");
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

  public interface FindUsagesHandler extends UsageViewUtil.UsageViewHandler {
    boolean canFindUsagesFor(PsiElement element);

    FindUsagesDialog createFindUsagesDialog(PsiElement element,
                                            Project project,
                                            FindUsagesOptions findUsagesOptions,
                                            boolean isSingleFile,
                                            boolean isOpenInNewTab,
                                            boolean isOpenInNewTabEnabled);

    String getHelpId(PsiElement element);
  }

  static public class HtmlFindUsagesHandler implements FindUsagesHandler {
    private FindUsagesHandler styleHandler;

    public void setStyleHandler(FindUsagesHandler handler) {
      styleHandler = handler;
    }

    public boolean canFindUsagesFor(PsiElement element) {
      boolean result = element instanceof XmlElementDecl || element instanceof XmlAttributeDecl;

      if (!result && styleHandler != null) {
        result = styleHandler.canFindUsagesFor(element);
      }
      return result;
    }

    public FindUsagesDialog createFindUsagesDialog(final PsiElement element,
                                                   Project project,
                                                   FindUsagesOptions findUsagesOptions,
                                                   boolean isSingleFile,
                                                   boolean isOpenInNewTab,
                                                   boolean isOpenInNewTabEnabled) {
      if (styleHandler != null && styleHandler.canFindUsagesFor(element)) {
        return styleHandler.createFindUsagesDialog(element, project, findUsagesOptions, isSingleFile, isOpenInNewTab,
                                                   isOpenInNewTabEnabled);
      }

      return new FindUsagesDialog(element,project,findUsagesOptions,isOpenInNewTab,isOpenInNewTabEnabled,isSingleFile) {
        private StateRestoringCheckBox myCbUsages;

        public FindUsagesOptions getShownOptions() {
          return new FindUsagesOptions(element.getProject(), SearchScopeCache.getInstance(element.getProject()));
        }

        protected void update() {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        protected JPanel createFindWhatPanel() {
          JPanel findWhatPanel = new JPanel();
          findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder("Find"));
          findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

          myCbUsages = addCheckboxToPanel("Usages", myFindUsagesOptions.isUsages, findWhatPanel, true, 'U');
          return findWhatPanel;
        }

        protected JComponent getPreferredFocusedControl() {
          return myCbUsages;
        }
      };
    }

    public String getType(PsiElement element) {
      if (styleHandler != null && styleHandler.canFindUsagesFor(element)) {
        return styleHandler.getType(element);
      }

      if (element instanceof XmlElementDecl || element instanceof XmlTag) {
        return "tag";
      } else if (element instanceof XmlAttributeDecl) {
        return "attribute";
      }

      return null;
    }

    public String getHelpId(PsiElement element) {
      if (styleHandler != null && styleHandler.canFindUsagesFor(element)) {
        return styleHandler.getHelpId(element);
      }
      return null;
    }

    public String getDescriptiveName(PsiElement element) {
      if (styleHandler != null && styleHandler.canFindUsagesFor(element)) {
        return styleHandler.getDescriptiveName(element);
      }
      return ((PsiNamedElement)element).getName();
    }

    public String getNodeText(PsiElement element, boolean useFullName) {
      if (styleHandler != null && styleHandler.canFindUsagesFor(element)) {
        return styleHandler.getNodeText(element, useFullName);
      }

      return ((PsiNamedElement)element).getName();
    }
  }

  static {
    HtmlFindUsagesHandler handler = new HtmlFindUsagesHandler();
    registerFindHandler(StdFileTypes.HTML, handler);
    registerFindHandler(StdFileTypes.XHTML, handler);
    registerFindHandler(StdFileTypes.DTD, handler);
    registerFindHandler(StdFileTypes.XML, handler);

    UsageViewUtil.registerUsageViewHandler(StdFileTypes.HTML, handler);
    UsageViewUtil.registerUsageViewHandler(StdFileTypes.XHTML, handler);
    UsageViewUtil.registerUsageViewHandler(StdFileTypes.DTD, handler);
    UsageViewUtil.registerUsageViewHandler(StdFileTypes.XML, handler);
  }

  private LastSearchData myLastSearchData = new LastSearchData();

  public FindUsagesManager(Project project, SearchScopeCache searchScopeCache, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;

    myFindPackageOptions = createFindUsagesOptions(searchScopeCache);
    myFindClassOptions = createFindUsagesOptions(searchScopeCache);
    myFindMethodOptions = createFindUsagesOptions(searchScopeCache);
    myFindVariableOptions = createFindUsagesOptions(searchScopeCache);
    myFindPointcutOptions = createFindUsagesOptions(searchScopeCache);
  }

  public static void registerFindHandler(FileType fileType, FindUsagesHandler handler) {
    findHandlers.put(fileType, handler);
  }

  public static FindUsagesHandler getFindHandler(FileType fileType) {
    return findHandlers.get(fileType);
  }

  private FindUsagesOptions createFindUsagesOptions(SearchScopeCache searchScopeCache) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(myProject, searchScopeCache);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isIncludeOverloadUsages = false;
    findUsagesOptions.isIncludeSubpackages = true;
    findUsagesOptions.isReadAccess = true;
    findUsagesOptions.isWriteAccess = true;
    findUsagesOptions.isCheckDeepInheritance = true;
    findUsagesOptions.isSearchInNonJavaFiles = true;
    return findUsagesOptions;
  }

  public boolean canFindUsages(final PsiElement element) {
    if (element == null) {
      return false;
    }

    if (!((element instanceof PsiDirectory) ||
           (element instanceof PsiClass) ||
           (element instanceof PsiVariable) ||
           (element instanceof PsiPointcutDef) ||
           (element instanceof PsiPackage) ||
           (ThrowSearchUtil.isSearchable(element)) ||
           (element instanceof PsiMethod))) {
      PsiFile containingFile = element.getContainingFile();
      FindUsagesHandler handler = (containingFile != null) ? getFindHandler(containingFile.getFileType()) : null;
      if (handler != null && handler.canFindUsagesFor(element)) return true;
      return false;
    }

    if (element instanceof PsiDirectory) {
      PsiPackage psiPackage = ((PsiDirectory)element).getPackage();
      if (psiPackage == null) {
        return false;
      }
      return psiPackage.getQualifiedName().length() != 0;
    }

    return true;
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

  private boolean findUsageInFile(FileEditor editor, int direction) {
    if (editor == null) {
      throw new IllegalArgumentException("editor cannot be null");
    }

    SmartPsiElementPointer[] lastSearchElements = myLastSearchData.myLastSearchElements;
    if (lastSearchElements == null) return false;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (int i = 0; i < lastSearchElements.length; i++) {
      SmartPsiElementPointer pointer = lastSearchElements[i];
      PsiElement element = pointer.getElement();
      if (element != null) elements.add(element);
    }
    if (elements.size() == 0) {
      Messages.showMessageDialog(myProject,
                                   "Searched elements have been changed.\n" +
                                   "Cannot search for usages.",
                                 "Cannot Search for Usages",
                                 Messages.getInformationIcon());
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


    findUsagesInEditor(allElements, psiFile, direction, options, textEditor);
    return true;
  }


  private void initLastSearchElement(final PsiElement[] elements, final FindUsagesOptions findUsagesOptions) {
    myLastSearchData.myLastSearchElements = new SmartPsiElementPointer[elements.length];
    for (int i = 0; i < elements.length; i++) {
      myLastSearchData.myLastSearchElements[i] = SmartPointerManager.getInstance(myProject)
        .createSmartPsiElementPointer(elements[i]);
    }
    myLastSearchData.myLastOptions = findUsagesOptions;
  }

  public void findUsages(PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor) {
    PsiElement[] elementsToSearch = null;
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

    boolean isOpenInNewTabEnabled;
    boolean toOpenInNewTab;
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    if (selectedContent != null && selectedContent.isPinned()) {
      toOpenInNewTab = true;
      isOpenInNewTabEnabled = false;
    }
    else {
      toOpenInNewTab = myToOpenInNewTab;
      isOpenInNewTabEnabled = (UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0);
    }

    if (psiElement instanceof PsiMethod || psiElement instanceof PsiPointcutDef) {
      psiElement = psiElement instanceof PsiMethod
        ?
        (PsiElement)SuperMethodWarningUtil.checkSuperMethod((PsiMethod)psiElement, "find usages of")
        :
        (PsiElement)SuperMethodWarningUtil.checkSuperPointcut((PsiPointcutDef)psiElement,
                                                              "find usages of");
    }

    if (psiElement == null) return;
    final FindUsagesDialog dialog = getFindUsagesDialog(psiElement, scopeFile != null, toOpenInNewTab, isOpenInNewTabEnabled);
    if (dialog == null) {
      return;
    }
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    if (isOpenInNewTabEnabled) {
      myToOpenInNewTab = toOpenInNewTab = dialog.isShowInSeparateWindow();
    }

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();

    if (scopeFile != null) {
      findUsagesOptions = (FindUsagesOptions)findUsagesOptions.clone();
      findUsagesOptions.isDerivedClasses = false;
      findUsagesOptions.isDerivedInterfaces = false;
      findUsagesOptions.isImplementingClasses = false;
    }

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
          final boolean findInInheritors;
          if (aClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            findInInheritors = true;
          }
          else {
            findInInheritors = Messages.showDialog(psiElement.getProject(),
                                                   "Do you want to search usages of parameter '" + parameter.getName() +
                                                     "' in overriding methods?",
                                                   "Search in Overriding Methods",
                                                     new String[]{"Yes", "No"},
                                                   0,
                                                   Messages.getQuestionIcon()) == 0;
          }
          if (findInInheritors) {
            elementsToSearch = getParameterElementsToSearch(parameter);
          }
        }
      }
    }
    else if (psiElement instanceof PsiField) {
      final PsiField field = (PsiField)psiElement;
      if (field.getContainingClass() != null && field.getType() != null) {
        final String propertyName = field.getManager().getCodeStyleManager().variableNameToPropertyName(field.getName(),
                                                                                                        VariableKind.FIELD);
        PsiMethod getter = PropertyUtil.
        findPropertyGetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                   ContainerUtil.iterate(field.getContainingClass().getMethods()));
        PsiMethod setter = PropertyUtil.
        findPropertySetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                   ContainerUtil.iterate(field.getContainingClass().getMethods()));
        if (getter != null || setter != null) {
          if (Messages.showDialog("Do you want to search for accessors of '" + field.getName() + "'?", "Search Accessors",
                                                                                                         new String[]{"Yes", "No"}, 0,
                                                                                                       Messages.getQuestionIcon()) ==
              DialogWrapper.OK_EXIT_CODE) {
            final List<PsiElement> elements = new ArrayList<PsiElement>();
            elements.add(field);
            if (getter != null) {
              getter = SuperMethodWarningUtil.checkSuperMethod(getter, "find usages of");
              if (getter == null) return;
              elements.add(getter);
            }
            if (setter != null) {
              setter = SuperMethodWarningUtil.checkSuperMethod(setter, "find usages of");
              if (setter == null) return;
              elements.add(setter);
            }

            elementsToSearch = elements.toArray(new PsiElement[elements.size()]);
          }
        }
      }
    }

    if (elementsToSearch == null) {
      elementsToSearch = new PsiElement[] {psiElement};
    }


    if (scopeFile == null) {
      findUsages(elementsToSearch, dialog.isSkipResultsWhenOneUsage(),
                 toOpenInNewTab, findUsagesOptions);
    }
    else {
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(elementsToSearch, scopeFile, FROM_START, findUsagesOptions, editor);
    }
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

  private UsageInfo[] findJoinPointsByPointcut(PsiPointcut pointcut) {
    final ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiSearchHelper psiSearchHelper = psiManager.getSearchHelper();

    psiSearchHelper.processJoinPointsByPointcut(new PsiBaseElementProcessor() {
                                                      public boolean execute(PsiElement element) {
                                                        usages.add(new UsageInfo(element));
                                                        return true;
                                                      }
                                                    }, pointcut, GlobalSearchScope.projectScope(myProject));

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  public void findPointcutApplications(final PsiPointcut pointcut) {
    final UsageInfo[][] usages = new UsageInfo[1][];

    final Runnable findUsagesRunnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
                                                                public void run() {
                                                                  usages[0] = findJoinPointsByPointcut(pointcut);
                                                                }
                                                              });
      }
    };

    final FindUsagesCommand findUsagesCommand = new FindUsagesCommand() {
      public UsageInfo[] execute(final PsiElement[] elementsToSearch) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
                                                                public void run() {
                                                                  if (elementsToSearch.length == 1 &&
                                                                    elementsToSearch[0] instanceof PsiPointcut &&
                                                                                                               elementsToSearch[0].isValid()) {
                                                                    usages[0] = findJoinPointsByPointcut((PsiPointcut)elementsToSearch[0]);
                                                                  }
                                                                  else {
                                                                    // should not happen
                                                                    LOG.assertTrue(false);
                                                                  }
                                                                }
                                                              });
        return usages[0];
      }

    };

    if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(findUsagesRunnable,
                                                                                 "Looking for applications of poincut(TODO)...", true,
                                                                                 myProject)) {
      return;
    }

    if ((usages[0] != null) && (usages[0].length > 0)) {
      FindUsagesViewDescriptor descriptor = new FindUsagesViewDescriptor(pointcut.getParent(), usages[0],
                                                                         findUsagesCommand, false); //TODO
      showUsagesPanel("Applications of pointcut(TODO)", null, descriptor, true, false, false);
    }
    else {
      Messages.showMessageDialog(myProject, "No applications(TODO) found", "Information",
                                 Messages.getInformationIcon());
    }
  }

  private UsageSearcher getUsageSearcher(final PsiElement[] elementsToSearch,
                                         final FindUsagesOptions options,
                                         final PsiFile scopeFile) {
    return new UsageSearcher() {
      public void generate(final Processor<Usage> processor) {
        if (scopeFile != null) {
          options.searchScope = new LocalSearchScope(scopeFile);
        }
        for (int i = 0; i < elementsToSearch.length; i++) {
          final PsiElement elementToSearch = elementsToSearch[i];
          if (elementToSearch != null && elementToSearch.isValid()) {
            FindUsagesUtil.processUsages(elementToSearch, new Processor<UsageInfo>() {
                                           public boolean process(UsageInfo usageInfo) {
                                             return processor.process(new UsageInfo2UsageAdapter(usageInfo));
                                           }
                                         }, options);
          }
        }
      }
    };
  }

  private void findUsages(final PsiElement[] elementsToSearch,
                          final boolean toSkipUsagePanelWhenOneUsage,
                          final boolean toOpenInNewTab,
                          final FindUsagesOptions findUsagesOptions) {
    PsiElement[] elementsToDisplay = elementsToSearch;
    PsiElement psiElement = elementsToSearch[0];
    if (findUsagesOptions.isIncludeOverloadUsages) {
      LOG.assertTrue(elementsToSearch.length == 1 && psiElement instanceof PsiMethod);
      elementsToDisplay = MethodSignatureUtil.getOverloads((PsiMethod)psiElement);
    }

    UsageViewPresentation presentation = createPresentation(psiElement, findUsagesOptions, toOpenInNewTab);

    Factory<UsageSearcher> searcherFactory = new Factory<UsageSearcher>() {
      public UsageSearcher create() {
        return getUsageSearcher(elementsToSearch, findUsagesOptions, null);
      }
    };

    myAnotherManager.searchAndShowUsages(convertTargets(elementsToDisplay),
                                         searcherFactory, !toSkipUsagePanelWhenOneUsage, true, presentation);
  }

  private UsageViewPresentation createPresentation(PsiElement psiElement,
                                                   final FindUsagesOptions findUsagesOptions,
                                                   boolean toOpenInNewTab) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = findUsagesOptions.searchScope != null ? findUsagesOptions.searchScope.getDisplayName() : null;
    presentation.setScopeText(scopeString);
    String usagesString = generateUsagesString(findUsagesOptions, false, true);
    presentation.setUsagesString(usagesString);
    String title = usagesString + " of " + UsageViewUtil.getShortName(psiElement);
    if (scopeString != null) title += " in " + scopeString;
    presentation.setTabText(title);
    presentation.setTargetsNodeText(UsageViewUtil.capitalize(UsageViewUtil.getType(psiElement)));
    presentation.setOpenInNewTab(toOpenInNewTab);
    return presentation;
  }

  private void findUsagesInEditor(final PsiElement[] elementsToSearch,
                                  final PsiFile scopeFile,
                                  final int direction,
                                  final FindUsagesOptions findUsagesOptions,
                                  FileEditor fileEditor) {
    LOG.assertTrue(fileEditor != null);
    initLastSearchElement(elementsToSearch, findUsagesOptions);

    clearStatusBar();

    final FileEditorLocation currentLocation = fileEditor.getCurrentLocation();

    final UsageSearcher usageSearcher = getUsageSearcher(elementsToSearch, findUsagesOptions, scopeFile);
    final boolean[] usagesWereFound = new boolean[]{false};

    Usage fUsage = findSiblingUsage(usageSearcher, direction, currentLocation, usagesWereFound, fileEditor);

    if (fUsage != null) {
      fUsage.navigate(true);
      fUsage.selectInEditor();
    }
    else if (!usagesWereFound[0]) {
      String message = getNoUsagesFoundMessage(elementsToSearch[0]);

      showHintOrStatusBarMessage(message, fileEditor);
    }
    else {
      fileEditor.putUserData(KEY_START_USAGE_AGAIN, "START_AGAIN");

      showHintOrStatusBarMessage(getSearchAgainMessage(elementsToSearch[0], direction), fileEditor);
    }
  }

  private String getNoUsagesFoundMessage(PsiElement psiElement) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    String message = "Usages of " + elementType + " " + elementName + " not found";
    return message;
  }

  private void clearStatusBar() {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    statusBar.setInfo("");
  }

  private String getSearchAgainMessage(PsiElement element, final int direction) {
    String message = getNoUsagesFoundMessage(element);
    if (direction == FindUsagesManager.AFTER_CARET) {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
      String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
      if (shortcutsText.length() > 0) {
        message += ", press " + shortcutsText;
      }
      else {
        message += ", perform \"Find Next\" again ";
      }
      message += " to search from the top";
    }
    else {
      String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(
        ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS));
      if (shortcutsText.length() > 0) {
        message += ", press " + shortcutsText;
      }
      else {
        message += ", perform \"Find Previous\" again ";
      }
      message += " to search from the bottom";
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
                               int dir,
                               final FileEditorLocation currentLocation,
                               final boolean[] usagesWereFound,
                               FileEditor fileEditor) {
    final Usage[] foundUsage = new Usage[]{null};

    if (fileEditor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      if (dir == AFTER_CARET) dir = FROM_START;
      else dir = FROM_END;
    }

    final int direction = dir;


    usageSearcher.generate(
      new Processor<Usage>() {
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

    fileEditor.putUserData(KEY_START_USAGE_AGAIN,  null);

    Usage fUsage = foundUsage[0];
    return fUsage;
  }

  private UsageTarget[] convertTargets(PsiElement[] elementsToDisplay) {
    UsageTarget[] targets = new UsageTarget[elementsToDisplay.length];
    for (int i = 0; i < targets.length; i++) {
      PsiElement display = elementsToDisplay[i];
      if (display instanceof NavigationItem) {
        final NavigationItem item = (NavigationItem)display;
        targets[i] = new PsiElement2UsageTargetAdapter((PsiElement)item);
      }
      else {
        throw new IllegalArgumentException("Wrong usage target:" + display);
      }
    }
    return targets;
  }

  private static String generateUsagesString(final FindUsagesOptions selectedOptions,
                                             final boolean useAndWord,
                                             boolean beginWithCapitals) {
    String usagesString = "";
    String suffix = useAndWord ? " and " : " or ";
    ArrayList<String> strings = new ArrayList<String>();
    FindUsagesOptions localShownFindUsagesOptions = selectedOptions;
    if ((selectedOptions.isUsages && localShownFindUsagesOptions.isUsages)
      || (selectedOptions.isClassesUsages && localShownFindUsagesOptions.isClassesUsages)
      || (selectedOptions.isMethodsUsages && localShownFindUsagesOptions.isMethodsUsages)
      || (selectedOptions.isFieldsUsages && localShownFindUsagesOptions.isFieldsUsages)) {
      strings.add(beginWithCapitals ? "Usages" : "usages");
    }
    if (selectedOptions.isIncludeOverloadUsages && localShownFindUsagesOptions.isIncludeOverloadUsages) {
      strings.add(beginWithCapitals ? "Overloaded Methods Usages" : "overloaded methods usages");
    }
    if ((selectedOptions.isDerivedClasses && localShownFindUsagesOptions.isDerivedClasses)) {
      strings.add(beginWithCapitals ? "Derived Classes" : "derived classes");
    }
    if ((selectedOptions.isDerivedInterfaces && localShownFindUsagesOptions.isDerivedInterfaces)) {
      strings.add(beginWithCapitals ? "Derived Interfaces" : "derived interfaces");
    }
    if ((selectedOptions.isImplementingClasses && localShownFindUsagesOptions.isImplementingClasses)) {
      strings.add(beginWithCapitals ? "Implementing Classes" : "implementing classes");
    }
    if ((selectedOptions.isImplementingMethods && localShownFindUsagesOptions.isImplementingMethods)) {
      strings.add(beginWithCapitals ? "Implementing Methods" : "implementing methods");
    }
    if ((selectedOptions.isOverridingMethods && localShownFindUsagesOptions.isOverridingMethods)) {
      strings.add(beginWithCapitals ? "Overriding Methods" : "overriding methods");
    }
    if (strings.size() == 0) {
      strings.add(beginWithCapitals ? "Usages" : "usages");
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
                                 HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                                 HintManager.HIDE_BY_SCROLLING,
                               0, false);
  }

  private void showUsagesPanel(String usagesString,
                               String scopeString,
                               final FindUsagesViewDescriptor viewDescriptor,
                               boolean toOpenInNewTab,
                               boolean showReadAccessIcon,
                               boolean showWriteAccessIcon) {
    String title = usagesString + " of " + UsageViewUtil.getShortName(viewDescriptor.getElement());
    if (scopeString != null) title += " in " + scopeString;
    UsageViewManager.getInstance(myProject).addContent(title, viewDescriptor, true,
                                                       showReadAccessIcon, showWriteAccessIcon, toOpenInNewTab, true);
  }

  public static String getHelpID(PsiElement element) {
    String helpID = null;
    if (element instanceof PsiPackage) {
      helpID = HelpID.FIND_PACKAGE_USAGES;
    }
    else if (element instanceof PsiClass) {
      helpID = HelpID.FIND_CLASS_USAGES;
    }
    else if (element instanceof PsiMethod) {
      helpID = HelpID.FIND_METHOD_USAGES;
    }
    else if (ThrowSearchUtil.isSearchable(element)) {
      helpID = HelpID.FIND_THROW_USAGES;
    }
    else if (element instanceof PsiField) {
      helpID = HelpID.FIND_FIELD_USAGES;
    }
    else if (element instanceof PsiLocalVariable) {
      helpID = HelpID.FIND_VARIABLE_USAGES;
    }
    else if (element instanceof PsiParameter) {
      helpID = HelpID.FIND_PARAMETER_USAGES;
    }
    else {
      final FindUsagesHandler findHandler = getFindHandler(element.getContainingFile().getFileType());
      if (findHandler != null) {
        helpID = findHandler.getHelpId(element);
      }
    }
    return helpID;
  }

  private FindUsagesDialog getFindUsagesDialog(PsiElement element,
                                               boolean isSingleFile,
                                               boolean isOpenInNewTab,
                                               boolean isOpenInNewTabEnabled) {
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, myProject, myFindPackageOptions, isOpenInNewTab,
                                         isOpenInNewTabEnabled, isSingleFile);
    }
    else if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, myProject, myFindClassOptions, isOpenInNewTab, isOpenInNewTabEnabled,
                                       isSingleFile);
    }
    else if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, myProject, myFindMethodOptions, isOpenInNewTab, isOpenInNewTabEnabled,
                                        isSingleFile);
    }
    else if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, myProject, myFindVariableOptions, isOpenInNewTab,
                                          isOpenInNewTabEnabled, isSingleFile);
    }
    else if (element instanceof PsiPointcutDef) {
      return new FindPointcutUsagesDialog(element, myProject, myFindPointcutOptions, isOpenInNewTab,
                                          isOpenInNewTabEnabled, isSingleFile);
    }
    else if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, myProject, myFindPointcutOptions, isOpenInNewTab,
                                       isOpenInNewTabEnabled, isSingleFile);
    }
    else {
      PsiFile containingFile = element.getContainingFile();
      FindUsagesHandler handler = (containingFile != null) ? getFindHandler(containingFile.getFileType()) : null;
      if (handler != null) {
        return handler.createFindUsagesDialog(element, myProject, createFindUsagesOptions(SearchScopeCache.getInstance(myProject)),
                                              isSingleFile, isOpenInNewTab, isOpenInNewTabEnabled);
      }
      return null;
    }
  }

}