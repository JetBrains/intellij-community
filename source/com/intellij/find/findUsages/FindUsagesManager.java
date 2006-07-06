package com.intellij.find.findUsages;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import static com.intellij.find.findUsages.FindUsagesManager.FileSearchScope.*;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Function;
import com.intellij.ide.util.SuperMethodWarningUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FindUsagesManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findParameterUsages.FindUsagesManager");
  private DefaultFindUsagesHandler myDefaultFindUsagesHandler;

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
  private final List<Function<PsiElement,FindUsagesHandler>> myHandlers = new ArrayList<Function<PsiElement, FindUsagesHandler>>();

  private static class LastSearchData {
    public SmartPsiElementPointer[] myLastSearchElements = null;
    public FindUsagesOptions myLastOptions = null;
  }

  private LastSearchData myLastSearchData = new LastSearchData();

  public FindUsagesManager(final Project project, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;
    final FindUsagesOptions findClassOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findMethodOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findPackageOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findPointcutOptions = FindUsagesHandler.createFindUsagesOptions(project);
    final FindUsagesOptions findVariableOptions = FindUsagesHandler.createFindUsagesOptions(project);

    myHandlers.add(new Function<PsiElement, FindUsagesHandler>() {
      @Nullable
      public FindUsagesHandler fun(final PsiElement element) {
        if (element instanceof PsiFile && ((PsiFile)element).getVirtualFile() == null) return null;
        if (!element.getLanguage().getFindUsagesProvider().canFindUsagesFor(element)) return null;
        if (element instanceof PsiDirectory) {
          PsiPackage psiPackage = ((PsiDirectory)element).getPackage();
          return psiPackage == null ? null : new DefaultFindUsagesHandler(psiPackage, findClassOptions, findMethodOptions,
                                                                          findPackageOptions, findPointcutOptions, findVariableOptions);
        }

        if (element instanceof PsiMethod) {
          final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, DefaultFindUsagesHandler.ACTION_STRING);
          if (methods.length > 1) {
            return new DefaultFindUsagesHandler(element, methods, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
          }
          if (methods.length == 1) {
            return new DefaultFindUsagesHandler(methods[0], findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
          }
          return null;
        }

        return new DefaultFindUsagesHandler(element, findClassOptions, findMethodOptions, findPackageOptions, findPointcutOptions, findVariableOptions);
      }
    });
  }

  public static FindUsagesProvider getFindHandler(PsiElement elt) {
    return elt.getLanguage().getFindUsagesProvider();
  }

  public Project getProject() {
    return myProject;
  }

  public void registerFindUsagesHandler(Function<PsiElement,FindUsagesHandler> handler) {
    myHandlers.add(0, handler);
  }

  public boolean canFindUsages(final PsiElement element) {
    return getHandler(element) != null;
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

  @Nullable
  private FindUsagesHandler getHandler(PsiElement element) {
    if (element == null) return null;
    for (final Function<PsiElement,FindUsagesHandler> function : myHandlers) {
      final FindUsagesHandler handler = function.fun(element);
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }

  public void findUsages(PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor) {
    final FindUsagesHandler handler = getHandler(psiElement);
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
      findUsagesInEditor(descriptor, scopeFile, FROM_START, findUsagesOptions, editor);
    }
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

  private static void convertToUsageTarget(final List<PsiElement2UsageTargetAdapter> targets, PsiElement elementToSearch) {
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


}