package com.intellij.find.findUsages;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public class FindUsagesManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findParameterUsages.FindUsagesManager");

  private static enum FileSearchScope {
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
  private final List<Function<PsiElement,Factory<FindUsagesHandler>>> myHandlers = new ArrayList<Function<PsiElement, Factory<FindUsagesHandler>>>();

  public static class SearchData {
    public SmartPsiElementPointer[] myElements = null;
    public FindUsagesOptions myOptions = null;

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SearchData that = (SearchData)o;

      if (!Arrays.equals(myElements, that.myElements)) return false;
      if (myOptions != null ? !myOptions.equals(that.myOptions) : that.myOptions != null) return false;

      return true;
    }

    public int hashCode() {
      return myElements != null ? Arrays.hashCode(myElements) : 0;
    }
  }

  private SearchData myLastSearchInFileData = new SearchData();
  private final CopyOnWriteArrayList<SearchData> myFindUsagesHistory = new CopyOnWriteArrayList<SearchData>();

  public FindUsagesManager(final Project project, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;
    final FindUsagesOptions findClassOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findMethodOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findPackageOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findPointcutOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findVariableOptions = FindUsagesHandler.createFindUsagesOptions(project);

    myHandlers.add(new Function<PsiElement, Factory<FindUsagesHandler>>() {
      @Nullable
      public Factory<FindUsagesHandler> fun(final PsiElement element) {
        if (element instanceof PsiFile) {
          if (((PsiFile)element).getVirtualFile() == null) return null;
        } else if (!element.getLanguage().getFindUsagesProvider().canFindUsagesFor(element)) {
          return null;
        }

        if (element instanceof PsiDirectory) {
          final PsiPackage psiPackage = ((PsiDirectory)element).getPackage();
          return psiPackage == null ? null : new Factory<FindUsagesHandler>() {
            public FindUsagesHandler create() {
              return new DefaultFindUsagesHandler(psiPackage, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
            }
          };
        }

        if (element instanceof PsiMethod) {
          return new Factory<FindUsagesHandler>() {
            @Nullable
            public FindUsagesHandler create() {
              final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, DefaultFindUsagesHandler.ACTION_STRING);
              if (methods.length > 1) {
                return new DefaultFindUsagesHandler(element, methods, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
              }
              if (methods.length == 1) {
                return new DefaultFindUsagesHandler(methods[0], findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
              }
              return null;
            }
          };
        }

        return new Factory<FindUsagesHandler>() {
          public FindUsagesHandler create() {
            return new DefaultFindUsagesHandler(element, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
          }
        };
      }
    });
  }

  public void registerFindUsagesHandler(Function<PsiElement,Factory<FindUsagesHandler>> handler) {
    myHandlers.add(0, handler);
  }

  public boolean canFindUsages(final PsiElement element) {
    return element != null && !ContainerUtil.mapNotNull(myHandlers, new Function<Function<PsiElement, Factory<FindUsagesHandler>>, Factory<FindUsagesHandler>>() {
      public Factory<FindUsagesHandler> fun(final Function<PsiElement, Factory<FindUsagesHandler>> function) {
        return function.fun(element);
      }
    }).isEmpty();
  }

  public void clearFindingNextUsageInFile() {
    myLastSearchInFileData.myOptions = null;
    myLastSearchInFileData.myElements = null;
  }

  public boolean findNextUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, FileSearchScope.AFTER_CARET);
  }

  public boolean findPreviousUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, FileSearchScope.BEFORE_CARET);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myToOpenInNewTab = JDOMExternalizer.readBoolean(element, "OPEN_NEW_TAB");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element, "OPEN_NEW_TAB", myToOpenInNewTab);
  }

  private boolean findUsageInFile(@NotNull FileEditor editor, FileSearchScope direction) {
    PsiElement[] elements = restorePsiElements(myLastSearchInFileData, true);
    if (elements == null) return false;
    if (elements.length == 0) return true;//all elements have invalidated

    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(elements);

    //todo
    TextEditor textEditor = (TextEditor)editor;
    Document document = textEditor.getEditor().getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return false;

    findUsagesInEditor(descriptor, psiFile, direction, myLastSearchInFileData.myOptions, textEditor);
    return true;
  }

  // returns null if cannot find, empty Pair if all elements have been changed
  @Nullable
  private PsiElement[] restorePsiElements(SearchData searchData, final boolean showErrorMessage) {
    if (searchData == null) return null;
    SmartPsiElementPointer[] lastSearchElements = searchData.myElements;
    if (lastSearchElements == null) return null;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (SmartPsiElementPointer pointer : lastSearchElements) {
      PsiElement element = pointer.getElement();
      if (element != null) elements.add(element);
    }
    if (elements.isEmpty() && showErrorMessage) {
      Messages.showMessageDialog(myProject, FindBundle.message("find.searched.elements.have.been.changed.error"),
                                 FindBundle.message("cannot.search.for.usages.title"), Messages.getInformationIcon());
      // SCR #10022
      //clearFindingNextUsageInFile();
      return PsiElement.EMPTY_ARRAY;
    }

    return elements.toArray(new PsiElement[elements.size()]);
  }

  private void initLastSearchElement(final FindUsagesOptions findUsagesOptions,
                                     UsageInfoToUsageConverter.TargetElementsDescriptor descriptor) {
    myLastSearchInFileData = createSearchData(descriptor.getAllElements(), findUsagesOptions);
  }

  private SearchData createSearchData(final List<? extends PsiElement> psiElements, final FindUsagesOptions findUsagesOptions) {
    SearchData data = new SearchData();

    data.myElements = new SmartPsiElementPointer[psiElements.size()];
    int idx = 0;
    for (PsiElement psiElement : psiElements) {
      data.myElements[idx++] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiElement);
    }
    data.myOptions = findUsagesOptions;
    return data;
  }

  @Nullable
  private FindUsagesHandler findHandler(PsiElement element) {
    for (final Function<PsiElement,Factory<FindUsagesHandler>> function : myHandlers) {
      final Factory<FindUsagesHandler> factory = function.fun(element);
      if (factory != null) {
        final FindUsagesHandler handler = factory.create();
        if (handler != null) {
          return handler;
        }
      }
    }
    return null;
  }

  public void findUsages(PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor) {
    final FindUsagesHandler handler = findHandler(psiElement);
    if (handler == null) return;

    final AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(scopeFile != null, shouldOpenInNewTab(), mustOpenInNewTab());
    dialog.show();
    if (!dialog.isOK()) return;

    setOpenInNewTab(dialog.isShowInSeparateWindow());

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();

    clearFindingNextUsageInFile();
    LOG.assertTrue(handler.getPsiElement().isValid());
    final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor =
      new UsageInfoToUsageConverter.TargetElementsDescriptor(handler.getPrimaryElements(), handler.getSecondaryElements());
    if (scopeFile == null) {
      findUsages(descriptor, dialog.isSkipResultsWhenOneUsage(), dialog.isShowInSeparateWindow(), findUsagesOptions);
    }
    else {
      findUsagesOptions = (FindUsagesOptions)findUsagesOptions.clone();
      findUsagesOptions.isDerivedClasses = false;
      findUsagesOptions.isDerivedInterfaces = false;
      findUsagesOptions.isImplementingClasses = false;
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(descriptor, scopeFile, FileSearchScope.FROM_START, findUsagesOptions, editor);
    }
  }


  private void setOpenInNewTab(final boolean toOpenInNewTab) {
    if (!mustOpenInNewTab()) {
      myToOpenInNewTab = toOpenInNewTab;
    }
  }

  private boolean shouldOpenInNewTab() {
    return mustOpenInNewTab() || myToOpenInNewTab;
  }

  private boolean mustOpenInNewTab() {
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    return selectedContent != null && selectedContent.isPinned();
  }


  private static UsageSearcher createUsageSearcher(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor, final FindUsagesOptions options,
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
        List<? extends PsiElement> elements =
          ApplicationManager.getApplication().runReadAction(new Computable<List<? extends PsiElement>>() {
            public List<? extends PsiElement> compute() {
              return descriptor.getAllElements();
            }
          });
        for (final PsiElement element : elements) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              LOG.assertTrue(element.isValid());
            }
          });
          FindUsagesUtil.processUsages(element, usageInfoProcessorToUsageProcessorAdapter, options);
        }
      }
    };
  }

  private static PsiElement2UsageTargetAdapter[] convertToUsageTargets(final List<? extends PsiElement> elementsToSearch) {
    final ArrayList<PsiElement2UsageTargetAdapter> targets = new ArrayList<PsiElement2UsageTargetAdapter>(elementsToSearch.size());
    for (PsiElement element : elementsToSearch) {
      convertToUsageTarget(targets, element);
    }
    return targets.toArray(new PsiElement2UsageTargetAdapter[targets.size()]);
  }

  private void findUsages(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                          final boolean toSkipUsagePanelWhenOneUsage,
                          final boolean toOpenInNewTab,
                          final FindUsagesOptions findUsagesOptions) {

    List<? extends PsiElement> elements = descriptor.getAllElements();
    final UsageTarget[] targets = convertToUsageTargets(elements);
    myAnotherManager.searchAndShowUsages(targets, new Factory<UsageSearcher>() {
      public UsageSearcher create() {
        return createUsageSearcher(descriptor, findUsagesOptions, null);
      }
    }, !toSkipUsagePanelWhenOneUsage, true, createPresentation(elements.get(0), findUsagesOptions, toOpenInNewTab), null);
    addToHistory(elements, findUsagesOptions);
  }

  private static UsageViewPresentation createPresentation(PsiElement psiElement,
                                                   final FindUsagesOptions findUsagesOptions,
                                                   boolean toOpenInNewTab) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = findUsagesOptions.searchScope != null ? findUsagesOptions.searchScope.getDisplayName() : null;
    presentation.setScopeText(scopeString);
    String usagesString = generateUsagesString(findUsagesOptions);
    presentation.setUsagesString(usagesString);
    String title;
    if (scopeString != null) {
      title = FindBundle.message("find.usages.of.element.in.scope.panel.title", usagesString, UsageViewUtil.getShortName(psiElement), scopeString);
    }
    else {
      title = FindBundle.message("find.usages.of.element.panel.title", usagesString, UsageViewUtil.getShortName(psiElement));
    }
    presentation.setTabText(title);
    presentation.setTabName(FindBundle.message("find.usages.of.element.tab.name", usagesString, UsageViewUtil.getShortName(psiElement)));
    presentation.setTargetsNodeText(StringUtil.capitalize(UsageViewUtil.getType(psiElement)));
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

  private static String getNoUsagesFoundMessage(PsiElement psiElement) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    return FindBundle.message("find.usages.of.element_type.element_name.not.found.message", elementType, elementName);
  }

  private void clearStatusBar() {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    statusBar.setInfo("");
  }

  private static String getSearchAgainMessage(PsiElement element, final FileSearchScope direction) {
    String message = getNoUsagesFoundMessage(element);
    if (direction == FileSearchScope.AFTER_CARET) {
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

  private static Usage findSiblingUsage(final UsageSearcher usageSearcher,
                                 FileSearchScope dir,
                                 final FileEditorLocation currentLocation,
                                 final boolean[] usagesWereFound,
                                 FileEditor fileEditor) {
    if (fileEditor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      if (dir == FileSearchScope.AFTER_CARET) {
        dir = FileSearchScope.FROM_START;
      }
      else {
        dir = FileSearchScope.FROM_END;
      }
    }

    final FileSearchScope direction = dir;


    final Usage[] foundUsage = new Usage[]{null};
    usageSearcher.generate(new Processor<Usage>() {
      public boolean process(Usage usage) {
        usagesWereFound[0] = true;

        if (direction == FileSearchScope.FROM_START) {
          foundUsage[0] = usage;
          return false;
        }
        else if (direction == FileSearchScope.FROM_END) {
          foundUsage[0] = usage;
        }
        else if (direction == FileSearchScope.AFTER_CARET) {
          if (usage.getLocation().compareTo(currentLocation) > 0) {
            foundUsage[0] = usage;
            return false;
          }
        }
        else if (direction == FileSearchScope.BEFORE_CARET) {
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

  private static void convertToUsageTarget(final List<PsiElement2UsageTargetAdapter> targets, PsiElement elementToSearch) {
    if (elementToSearch instanceof NavigationItem) {
      targets.add(new PsiElement2UsageTargetAdapter(elementToSearch));
    }
    else {
      throw new IllegalArgumentException("Wrong usage target:" + elementToSearch);
    }
  }

  private static String generateUsagesString(final FindUsagesOptions selectedOptions) {
    String suffix = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    ArrayList<String> strings = new ArrayList<String>();
    if (selectedOptions.isUsages
        || selectedOptions.isClassesUsages ||
        selectedOptions.isMethodsUsages ||
        selectedOptions.isFieldsUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    if (selectedOptions.isIncludeOverloadUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.overloaded.methods.usages"));
    }
    if (selectedOptions.isDerivedClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.classes"));
    }
    if (selectedOptions.isDerivedInterfaces) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.interfaces"));
    }
    if (selectedOptions.isImplementingClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.classes"));
    }
    if (selectedOptions.isImplementingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.methods"));
    }
    if (selectedOptions.isOverridingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.overriding.methods"));
    }
    if (strings.isEmpty()) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    String usagesString = "";
    for (int i = 0; i < strings.size(); i++) {
      String s = strings.get(i);
      usagesString += i == strings.size() - 1 ? s : s + suffix;
    }
    return usagesString;
  }

  private static void showEditorHint(String message, final Editor editor) {
    HintManager hintManager = HintManager.getInstance();
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    hintManager.showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
  }

  public static String getHelpID(PsiElement element) {
    return element.getLanguage().getFindUsagesProvider().getHelpId(element);
  }

  private void addToHistory(final List<? extends PsiElement> elements, final FindUsagesOptions findUsagesOptions) {
    SearchData data = createSearchData(elements, findUsagesOptions);
    myFindUsagesHistory.remove(data);
    myFindUsagesHistory.add(data);

    // todo configure history depth limit
    if (myFindUsagesHistory.size() > 15) {
      myFindUsagesHistory.remove(0);
    }
  }

  public void rerunAndRecallFromHistory(SearchData searchData) {
    myFindUsagesHistory.remove(searchData);
    PsiElement[] elements = restorePsiElements(searchData, true);
    if (elements == null || elements.length == 0) return;
    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(elements);
    findUsages(descriptor, false, false, searchData.myOptions);
  }

  // most recent entry is at the end of the list
  public List<SearchData> getFindUsageHistory() {
    removeInvalidElementsFromHistory();
    return Collections.unmodifiableList(myFindUsagesHistory);
  }

  private void removeInvalidElementsFromHistory() {
    for (SearchData data : myFindUsagesHistory) {
      PsiElement[] elements = restorePsiElements(data, false);
      if (elements == null || elements.length == 0) myFindUsagesHistory.remove(data);
    }
  }
}