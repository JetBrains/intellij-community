/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInsight.daemon.impl.PostHighlightingPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.DataManager;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestFixtureImpl extends BaseFixture implements CodeInsightTestFixture {

  @NonNls private static final String PROFILE = "Configurable";

  private PsiManagerImpl myPsiManager;
  private PsiFile myFile;
  private Editor myEditor;
  private String myTestDataPath;
  private boolean myEmptyLookup;

  private LocalInspectionTool[] myInspections;
  private final Map<String, LocalInspectionTool> myAvailableTools = new THashMap<String, LocalInspectionTool>();
  private final Map<String, LocalInspectionToolWrapper> myAvailableLocalTools = new THashMap<String, LocalInspectionToolWrapper>();

  private final TempDirTestFixture myTempDirFixture = new TempDirTestFixtureImpl();
  private final IdeaProjectTestFixture myProjectFixture;
  private final Set<VirtualFile> myAddedClasses = new THashSet<VirtualFile>();
  @NonNls private static final String XXX = "XXX";
  private PsiElement myFileContext;

  public CodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture) {
    myProjectFixture = projectFixture;
  }

  public void setTestDataPath(String dataPath) {
    myTestDataPath = dataPath;
  }

  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  public TempDirTestFixture getTempDirFixture() {
    return myTempDirFixture;
  }

  public VirtualFile copyFileToProject(@NonNls final String sourceFilePath, @NonNls final String targetPath) throws IOException {
    final File destFile = new File(getTempDirPath() + "/" + targetPath);
    if (!destFile.exists()) {
      File fromFile = new File(getTestDataPath() + "/" + sourceFilePath);
      if (!fromFile.exists()) {
        fromFile = new File(sourceFilePath);
      }

      if (fromFile.isDirectory()) {
        destFile.mkdirs();
      } else {
        FileUtil.copy(fromFile, destFile);
      }
    }
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
    Assert.assertNotNull(file);
    return file;
  }

  public VirtualFile copyDirectoryToProject(@NonNls final String sourceFilePath, @NonNls final String targetPath) throws IOException {
    final File destFile = new File(getTempDirPath() + "/" + targetPath);
    final File fromFile = new File(getTestDataPath() + "/" + sourceFilePath);
    FileUtil.copyDir(fromFile, destFile);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
    Assert.assertNotNull(file);
    file.refresh(false, true);
    return file;
  }

  public VirtualFile copyFileToProject(@NonNls final String sourceFilePath) throws IOException {
    return copyFileToProject(sourceFilePath, sourceFilePath);
  }

  public void enableInspections(LocalInspectionTool... inspections) {
    myInspections = inspections;
    if (isInitialized()) {
      configureInspections(myInspections);
    }
  }

  private boolean isInitialized() {
    return myPsiManager != null;
  }

  public void enableInspections(final Class<? extends LocalInspectionTool>... inspections) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (Class clazz: inspections) {
      try {
        LocalInspectionTool inspection = (LocalInspectionTool)clazz.getConstructor().newInstance();
        tools.add(inspection);
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot instantiate " + clazz);
      }
    }
    enableInspections(tools.toArray(new LocalInspectionTool[tools.size()]));
  }

  public void disableInspections(LocalInspectionTool... inspections) {
    myAvailableTools.clear();
    myAvailableLocalTools.clear();
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>(Arrays.asList(myInspections));
    for (Iterator<LocalInspectionTool> i = tools.iterator(); i.hasNext();) {
      final LocalInspectionTool tool = i.next();
      for (LocalInspectionTool toRemove: inspections) {
        if (tool.getShortName().equals(toRemove.getShortName())) {
          i.remove();
          break;
        }
      }
    }
    myInspections = tools.toArray(new LocalInspectionTool[tools.size()]);
    configureInspections(myInspections);
  }

  public void enableInspections(InspectionToolProvider... providers) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (InspectionToolProvider provider: providers) {
      for (Class clazz: provider.getInspectionClasses()) {
        try {
          LocalInspectionTool inspection = (LocalInspectionTool)clazz.getConstructor().newInstance();
          tools.add(inspection);
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot instantiate " + clazz);
        }
      }
    }
    myInspections = tools.toArray(new LocalInspectionTool[tools.size()]);
    configureInspections(myInspections);
  }

  public long testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               final String... filePaths) throws Throwable {

    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        configureByFilesInner(filePaths);
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public long checkHighlighting(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings) throws Throwable {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Throwable {
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public long checkHighlighting() throws Throwable {
    return checkHighlighting(true, true, true);
  }

  public long testHighlighting(final String... filePaths) throws Throwable {
    return testHighlighting(true, true, true, filePaths);
  }

  public long testHighlighting(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings, final VirtualFile file) throws Throwable {
    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Throwable {
        myFile = myPsiManager.findFile(file);
        myEditor = createEditor(file);
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  @Nullable
  public PsiReference getReferenceAtCaretPosition(final String... filePaths) throws Throwable {
    new WriteCommandAction<PsiReference>(myProjectFixture.getProject()) {
      protected void run(final Result<PsiReference> result) throws Throwable {
        configureByFilesInner(filePaths);
      }
    }.execute().throwException();
    return getFile().findReferenceAt(myEditor.getCaretModel().getOffset());
  }

  @NotNull
  public PsiReference getReferenceAtCaretPositionWithAssertion(final String... filePaths) throws Throwable {
    final PsiReference reference = getReferenceAtCaretPosition(filePaths);
    assert reference != null: "no reference found at " + myEditor.getCaretModel().getLogicalPosition();
    return reference;
  }

  @NotNull
  public List<IntentionAction> getAvailableIntentions(final String... filePaths) throws Throwable {

    final Project project = myProjectFixture.getProject();
    return new WriteCommandAction<List<IntentionAction>>(project) {
      protected void run(final Result<List<IntentionAction>> result) throws Throwable {
        configureByFilesInner(filePaths);
        result.setResult(CodeInsightTestFixtureImpl.this.getAvailableIntentions());
      }
    }.execute().getResultObject();
  }

  @NotNull
  public List<IntentionAction> getAvailableIntentions() {
    int offset = myEditor.getCaretModel().getOffset();
    return getAvailableIntentions(myProjectFixture.getProject(), doHighlighting(), offset, myEditor, myFile);
  }

  public IntentionAction getAvailableIntention(final String intentionName, final String... filePaths) throws Throwable {
    List<IntentionAction> intentions = getAvailableIntentions(filePaths);
    return CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
  }

  public void launchAction(@NotNull final IntentionAction action) throws Throwable {
    new WriteCommandAction(myProjectFixture.getProject()) {
      protected void run(final Result result) throws Throwable {
        action.invoke(getProject(), getEditor(), getFile());
      }
    }.execute().throwException();

  }

  public void testCompletion(final String[] filesBefore, final String fileAfter) throws Throwable {
    assertInitialized();
    configureByFiles(filesBefore);
    final LookupElement[] items = complete(CompletionType.BASIC);
    if (items != null) {
      System.out.println("items = " + Arrays.toString(items));
    }
    checkResultByFile(fileAfter);
  }

  private void assertInitialized() {
    Assert.assertNotNull("setUp() hasn't been called", myPsiManager);
  }

  public void testCompletion(String fileBefore, String fileAfter, final String... additionalFiles) throws Throwable {
    testCompletion(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)), fileAfter);
  }

  public void testCompletionVariants(final String fileBefore, final String... expectedItems) throws Throwable {
    assertInitialized();
    final List<String> result = getCompletionVariants(fileBefore);
    UsefulTestCase.assertSameElements(result, expectedItems);
  }

  public List<String> getCompletionVariants(final String fileBefore) throws Throwable {
    assertInitialized();
    configureByFiles(fileBefore);
    final LookupElement[] items = complete(CompletionType.BASIC);
    Assert.assertNotNull("No lookup was shown, probably there was only one lookup element that was inserted automatically", items);
    return getLookupElementStrings();
  }

  @Nullable
  public List<String> getLookupElementStrings() {
    assertInitialized();
    final LookupElement[] elements = getLookupElements();
    if (elements == null) return null;

    return ContainerUtil.map(elements, new Function<LookupElement, String>() {
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    });
  }

  public void testRename(final String fileBefore, final String fileAfter, final String newName, final String... additionalFiles) throws Throwable {
    assertInitialized();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)));
    testRename(fileAfter, newName);
  }

  public void testRename(final String fileAfter, final String newName) throws Throwable {
    assertInitialized();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Throwable {
        PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
        assert element != null: "element not found in file " + myFile.getName() + " at caret position, offset " + myEditor.getCaretModel().getOffset();
        final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor);
        new RenameProcessor(myProjectFixture.getProject(), substitution, newName, false, false).run();
        checkResultByFile(fileAfter, myFile, false);
      }
    }.execute().throwException();
  }

  public void type(final char c) {
    assertInitialized();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    if (c == '\b') {
      performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
      return;
    }
    if (c == '\n') {
      performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
      return;
    }
    if (c == '\t') {
      if (LookupManager.getInstance(getProject()).getActiveLookup() != null) {
        actionManager.getActionHandler(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE).execute(getEditor(), dataContext);
        return;
      }
    }

    actionManager.getTypedAction().actionPerformed(getEditor(), c, dataContext);
  }

  public void performEditorAction(final String actionId) {
    assertInitialized();
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    actionManager.getActionHandler(actionId).execute(getEditor(), dataContext);
  }

  public JavaPsiFacade getJavaFacade() {
    assertInitialized();
    return JavaPsiFacade.getInstance(getProject());
  }

  public Collection<UsageInfo> testFindUsages(@NonNls final String... fileNames) throws Throwable {
    assertInitialized();
    configureByFiles(fileNames);
    PsiElement referenceTo = TargetElementUtilBase
      .findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);

    assert referenceTo != null : "Cannot find referenced element";
    final Project project = getProject();
    final FindUsagesHandler handler = new FindUsagesManager(project, null).getFindUsagesHandler(referenceTo, false);

    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
    final FindUsagesOptions options = new FindUsagesOptions(project);
    options.isUsages = true;
    assert handler != null : "Cannot find handler for: " + referenceTo;
    final PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements(), PsiElement.class);
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }

  public void moveFile(@NonNls final String filePath, @NonNls final String to, final String... additionalFiles) throws Throwable {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, filePath)));
        final VirtualFile file = findFileInTempDir(to);
        assert file.isDirectory() : to + " is not a directory";
        final PsiDirectory directory = myPsiManager.findDirectory(file);
        new MoveFilesOrDirectoriesProcessor(project, new PsiElement[] {myFile}, directory,
                                            false, false, null, null).run();
      }
    }.execute().throwException();

  }

  @Nullable
  public GutterIconRenderer findGutter(final String filePath) throws Throwable {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    final Ref<GutterIconRenderer> result = new Ref<GutterIconRenderer>();
    new WriteCommandAction.Simple(project) {

      protected void run() throws Throwable {
        final int offset = configureByFilesInner(filePath);

        final Collection<HighlightInfo> infos = doHighlighting();
        for (HighlightInfo info :infos) {
          if (info.endOffset >= offset && info.startOffset <= offset) {
            final GutterIconRenderer renderer = info.getGutterIconRenderer();
            if (renderer != null) {
              result.set(renderer);
              return;
            }
          }
        }

      }
    }.execute().throwException();
    return result.get();
  }

  @NotNull
  public Collection<GutterIconRenderer> findAllGutters(final String filePath) throws Throwable {
    assertInitialized();
    final Project project = myProjectFixture.getProject();
    final SortedMap<HighlightInfo, List<GutterIconRenderer>> result = new TreeMap<HighlightInfo, List<GutterIconRenderer>>(new Comparator<HighlightInfo>() {
      public int compare(final HighlightInfo o1, final HighlightInfo o2) {
        return o1.startOffset - o2.startOffset;
      }
    });
    new WriteCommandAction.Simple(project) {

      protected void run() throws Throwable {
        configureByFilesInner(filePath);

        final Collection<HighlightInfo> infos = doHighlighting();
        for (HighlightInfo info :infos) {
          final GutterIconRenderer renderer = info.getGutterIconRenderer();
          if (renderer != null) {
            List<GutterIconRenderer> renderers = result.get(info);
            if (renderers == null) {
              result.put(info, renderers = new SmartList<GutterIconRenderer>());
            }
            renderers.add(renderer);
          }
        }

      }
    }.execute().throwException();
    return ContainerUtil.concat(result.values());
  }

  public PsiClass addClass(@NotNull @NonNls final String classText) throws IOException {
    assertInitialized();
    final PsiClass psiClass = ((HeavyIdeaTestFixture)myProjectFixture).addClass(getTempDirPath(), classText);
    myAddedClasses.add(psiClass.getContainingFile().getVirtualFile());
    return psiClass;
  }

  public PsiFile addFileToProject(@NonNls final String relativePath, @NonNls final String fileText) throws IOException {
    assertInitialized();
    return ((HeavyIdeaTestFixture)myProjectFixture).addFileToProject(getTempDirPath(), relativePath, fileText);
  }

  public <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> epName, final T extension) {
    assertInitialized();
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(epName);
    extensionPoint.registerExtension(extension);
    disposeOnTearDown(new Disposable() {
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }

  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  public LookupElement[] complete(final CompletionType type) {
    assertInitialized();
    myEmptyLookup = false;
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        final CodeInsightActionHandler handler = new CodeCompletionHandlerBase(type) {
          protected PsiFile createFileCopy(final PsiFile file) {
            final PsiFile copy = super.createFileCopy(file);
            if (myFileContext != null) {
              final PsiElement contextCopy = myFileContext.copy();
              final PsiFile containingFile = contextCopy.getContainingFile();
              if (containingFile instanceof PsiFileImpl) {
                ((PsiFileImpl)containingFile).setOriginalFile(myFileContext.getContainingFile());
              }
              setContext(copy, contextCopy);
            }
            return copy;
          }

          @Override
          protected void completionFinished(final int offset1, final int offset2, final CompletionContext context, final CompletionProgressIndicator indicator,
                                            final LookupElement[] items) {
            myEmptyLookup = items.length == 0;
            super.completionFinished(offset1, offset2, context, indicator, items);
          }
        };
        Editor editor = getCompletionEditor();
        handler.invoke(getProject(), editor, PsiUtilBase.getPsiFileInEditor(editor, getProject()));
      }
    }.execute();
    return getLookupElements();
  }

  @Nullable
  protected Editor getCompletionEditor() {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile);
  }

  @Nullable
  public LookupElement[] completeBasic() {
    return complete(CompletionType.BASIC);
  }

  @Nullable 
  public LookupElement[] getLookupElements() {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup == null) {
      return myEmptyLookup ? LookupElement.EMPTY_ARRAY : null;
    }
    else {
      return lookup.getSortedItems();
    }
  }

  public void checkResult(final String text) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    checkResult("TEXT", false, SelectionAndCaretMarkupLoader.fromText(text, getProject()), myFile.getText());
  }

  public void checkResultByFile(final String expectedFile) throws Throwable {
    checkResultByFile(expectedFile, false);
  }

  public void checkResultByFile(final String expectedFile, final boolean ignoreWhitespaces) throws Throwable {
    assertInitialized();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        checkResultByFile(expectedFile, myFile, ignoreWhitespaces);
      }
    }.execute().throwException();
  }

  public void checkResultByFile(final String filePath, final String expectedFile, final boolean ignoreWhitespaces) throws Throwable {
    assertInitialized();

    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        String fullPath = getTempDirPath() + "/" + filePath;
        final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
        assert copy != null : "file not found: " + fullPath;
        final PsiFile psiFile = myPsiManager.findFile(copy);
        assert psiFile != null;
        checkResultByFile(expectedFile, psiFile, ignoreWhitespaces);
      }
    }.execute().throwException();
  }

  public void setUp() throws Exception {
    super.setUp();

    myProjectFixture.setUp();
    myTempDirFixture.setUp();
    myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
    configureInspections(myInspections == null ? new LocalInspectionTool[0] : myInspections);
  }

  private void enableInspectionTool(LocalInspectionTool tool){
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName, tool.getDisplayName(), tool.getID());
    }
    myAvailableTools.put(shortName, tool);
    myAvailableLocalTools.put(shortName, new LocalInspectionToolWrapper(tool));
  }

  private void configureInspections(final LocalInspectionTool[] tools) {
    for (LocalInspectionTool tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      public InspectionProfileEntry[] getInspectionTools() {
        final Collection<LocalInspectionToolWrapper> tools = myAvailableLocalTools.values();
        return tools.toArray(new LocalInspectionToolWrapper[tools.size()]);
      }

      public boolean isToolEnabled(HighlightDisplayKey key) {
        return key != null && key.toString() != null && myAvailableTools != null && myAvailableTools.containsKey(key.toString());
      }

      public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key) {
        final LocalInspectionTool localInspectionTool = key == null ? null : myAvailableTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      public InspectionTool getInspectionTool(String shortName) {
        return myAvailableLocalTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    Disposer.register(getProject(), new Disposable() {
      public void dispose() {
        inspectionProfileManager.deleteProfile(PROFILE);
      }
    });
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
  }

  public void tearDown() throws Exception {
    assertInitialized();
    if (SwingUtilities.isEventDispatchThread()) {
      LookupManager.getInstance(getProject()).hideActiveLookup();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          LookupManager.getInstance(getProject()).hideActiveLookup();
        }
      }, ModalityState.NON_MODAL);
    }

    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }

    myProjectFixture.tearDown();
    myTempDirFixture.tearDown();

    super.tearDown();
  }

  private int configureByFilesInner(@NonNls String... filePaths) throws IOException {
    assertInitialized();
    myFile = null;
    myEditor = null;
    for (int i = filePaths.length - 1; i > 0; i--) {
      configureByFileInner(filePaths[i]);
    }
    return configureByFileInner(filePaths[0]);
  }

  public void configureByFile(final String file) throws IOException {
    assertInitialized();
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Throwable {
        configureByFilesInner(file);
      }
    }.execute();
  }

  public void configureByFiles(@NonNls final String... files) throws Throwable {
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Throwable {
        configureByFilesInner(files);
      }
    }.execute();
  }

  public void configureByDirectory(final String dir) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiFile configureByText(final FileType fileType, @NonNls final String text) throws Throwable {
    assertInitialized();
    final String extension = fileType.getDefaultExtension();
    final File tempFile = File.createTempFile("aaa", "." + extension, new File(getTempDirPath()));
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager.getFileTypeByExtension(extension) != fileType) {
      fileTypeManager.associateExtension(fileType, extension);
    }
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    VfsUtil.saveText(vFile, text);
    assert vFile != null;
    configureInner(vFile, SelectionAndCaretMarkupLoader.fromFile(vFile, getProject()));
    return myFile;
  }

  public Document getDocument(final PsiFile file) {
    assertInitialized();
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  public void setFileContext(@Nullable final PsiElement context) {
    myFileContext = context;
    setContext(myFile, context);
  }

  /**
   *
   * @param filePath
   * @return caret offset or -1 if caret marker does not present
   * @throws IOException
   */
  private int configureByFileInner(@NonNls String filePath) throws IOException {
    assertInitialized();
    copyFileToProject(filePath);
    return configureFromTempProjectFile(filePath);
  }

  public int configureFromTempProjectFile(final String filePath) throws IOException {
    return configureByFileInner(findFileInTempDir(filePath));
  }

  public void configureFromExistingVirtualFile(VirtualFile f) throws IOException {
    configureByFileInner(f);
  }

  private int configureByFileInner(final VirtualFile copy) throws IOException {
    return configureInner(copy, SelectionAndCaretMarkupLoader.fromFile(copy, getProject()));
  }

  private int configureInner(@NotNull final VirtualFile copy, final SelectionAndCaretMarkupLoader loader) {
    assertInitialized();
    try {
      final OutputStream outputStream = copy.getOutputStream(null, 0, 0);
      outputStream.write(loader.newFileText.getBytes());
      outputStream.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myFile = myPsiManager.findFile(copy);
    setContext(myFile, myFileContext);
    myEditor = createEditor(copy);
    assert myEditor != null;
    int offset = -1;
    if (loader.caretMarker != null) {
      offset = loader.caretMarker.getStartOffset();
      myEditor.getCaretModel().moveToOffset(offset);
    }
    if (loader.selStartMarker != null && loader.selEndMarker != null) {
      myEditor.getSelectionModel().setSelection(loader.selStartMarker.getStartOffset(), loader.selEndMarker.getStartOffset());
    }

    Module module = getModule();
    if (module != null) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        module.getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
      }
    }

    return offset;
  }

  private static void setContext(final PsiFile file, final PsiElement context) {
    if (file != null && context != null) {
      file.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, new SmartPsiElementPointer() {
        public PsiElement getElement() {
          return context;
        }

        public PsiFile getContainingFile() {
          return context.getContainingFile();
        }
      });
    }
  }

  public VirtualFile findFileInTempDir(final String filePath) {
    String fullPath = getTempDirPath() + "/" + filePath;

    final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assert copy != null: "file " + fullPath + " not found";
    return copy;
  }

  @Nullable
  private Editor createEditor(VirtualFile file) {
    final Project project = getProject();
    final FileEditorManager instance = FileEditorManager.getInstance(project);
    if (file.getFileType().isBinary()) {
      return null;
    }
    return instance.openTextEditor(new OpenFileDescriptor(project, file, 0), false);
  }

  private void collectAndCheckHighlightings(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, Ref<Long> duration)
    throws Exception {
    final Project project = getProject();
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord(XXX, UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(project), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        if (myAddedClasses.contains(file)) return false;

        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };

    JavaPsiFacadeEx.getInstanceEx(project).setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    final long start = System.currentTimeMillis();
//    ProfilingUtil.startCPUProfiling();
    Collection<HighlightInfo> infos = doHighlighting();
    duration.set(System.currentTimeMillis() - start);
//    ProfilingUtil.captureCPUSnapshot("testing");

    JavaPsiFacadeEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());
  }

  @NotNull
  private Collection<HighlightInfo> doHighlighting() {

    final Project project = myProjectFixture.getProject();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(project, myFile, document, 0, myFile.getTextLength(), true);
    action1.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(project, myFile, myEditor, 0, myFile.getTextLength());
    action2.doCollectInformation(new MockProgressIndicator());
    Collection<HighlightInfo> highlights2 = action2.getHighlights();

    Collection<HighlightInfo> highlights3 = null;
    if (!myAvailableTools.isEmpty()) {
      LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(myFile, myEditor.getDocument(), 0, myFile.getTextLength());
      inspectionsPass.doCollectInformation(new MockProgressIndicator());
      highlights3 = inspectionsPass.getHighlights();
    }

    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>();
    list.addAll(highlights1);
    list.addAll(highlights2);
    if (highlights3 != null) {
      list.addAll(highlights3);
    }
    return list;
  }

  public String getTestDataPath() {
    return myTestDataPath;
  }

  public Project getProject() {
    return myProjectFixture.getProject();
  }

  public Module getModule() {
    return myProjectFixture.getModule();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public static List<IntentionAction> getAvailableIntentions(final Project project, final Collection<HighlightInfo> infos, final int offset,
                                                             final Editor editor, final PsiFile file) {
    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();

    for (HighlightInfo info :infos) {
      if (info.quickFixActionRanges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
          if (offset > 0 && !pair.getSecond().contains(offset)) {
            continue;
          }
          final HighlightInfo.IntentionActionDescriptor actionDescriptor = pair.first;
          final IntentionAction action = actionDescriptor.getAction();
          if (action.isAvailable(project, editor, file)) {
            availableActions.add(action);
            final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
            assert element != null;
            final List<IntentionAction> actions = actionDescriptor.getOptions(element);
            if (actions != null) {
              for (IntentionAction intentionAction : actions) {
                if (intentionAction.isAvailable(project, editor, file)) {
                  availableActions.add(intentionAction);
                }
              }
            }
          }
        }
      }
    }

    for (IntentionAction intentionAction : IntentionManager.getInstance().getIntentionActions()) {
      if (intentionAction.isAvailable(project, editor, file)) {
        availableActions.add(intentionAction);
      }
    }
    return availableActions;
  }

  static class SelectionAndCaretMarkupLoader {
    final String newFileText;
    final RangeMarker caretMarker;
    final RangeMarker selStartMarker;
    final RangeMarker selEndMarker;

    static SelectionAndCaretMarkupLoader fromFile(String path, Project project) throws IOException {
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(new File(path)))), project);
    }
    static SelectionAndCaretMarkupLoader fromFile(VirtualFile file, Project project) throws IOException {
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(VfsUtil.loadText(file)), project);
    }

    static SelectionAndCaretMarkupLoader fromText(String text, Project project) {
      return new SelectionAndCaretMarkupLoader(text, project);
    }

    private SelectionAndCaretMarkupLoader(String fileText, Project project) {
      final Document document = EditorFactory.getInstance().createDocument(fileText);

      int caretIndex = fileText.indexOf(CARET_MARKER);
      int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
      int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

      caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
      selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

      new WriteCommandAction(project) {
        protected void run(Result result) throws Throwable {
          if (caretMarker != null) {
            document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
          }
          if (selStartMarker != null) {
            document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
          }
          if (selEndMarker != null) {
            document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
          }
        }
      }.execute();

      newFileText = document.getText();
    }

  }
  private void checkResultByFile(@NonNls String expectedFile,
                                 @NotNull PsiFile originalFile,
                                 boolean stripTrailingSpaces) throws IOException {
    if (!stripTrailingSpaces) {
      final LogicalPosition position = myEditor.getCaretModel().getLogicalPosition();
      EditorUtil.fillVirtualSpaceUntil(myEditor, position.column, position.line);
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    checkResult(expectedFile, stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromFile(getTestDataPath() + "/" + expectedFile, getProject()), originalFile.getText());
  }

  private void checkResult(final String expectedFile,
                           final boolean stripTrailingSpaces,
                           final SelectionAndCaretMarkupLoader loader,
                           String actualText) {
    assertInitialized();
    Project project = myProjectFixture.getProject();

    project.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (stripTrailingSpaces) {
      actualText = stripTrailingSpaces(actualText);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String newFileText1 = loader.newFileText;
    if (stripTrailingSpaces) {
      newFileText1 = stripTrailingSpaces(newFileText1);
    }

    actualText = StringUtil.convertLineSeparators(actualText, "\n");

    //noinspection HardCodedStringLiteral
    Assert.assertEquals("Text mismatch in file " + expectedFile, newFileText1, actualText);

    if (loader.caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.caretMarker.getStartOffset());
      int caretCol = loader.caretMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, caretLine, 0);

      Assert.assertEquals("caretLine", caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      Assert.assertEquals("caretColumn", caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (loader.selStartMarker != null && loader.selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selStartMarker.getStartOffset());
      int selStartCol = loader.selStartMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selEndMarker.getEndOffset());
      int selEndCol = loader.selEndMarker.getEndOffset() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0);

      Assert.assertEquals("selectionStartLine", selStartLine + 1,
                          StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      Assert.assertEquals("selectionStartCol", selStartCol + 1, myEditor.getSelectionModel().getSelectionStart() -
                                                                StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0) + 1);

      Assert.assertEquals("selectionEndLine", selEndLine + 1,
                          StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      Assert.assertEquals("selectionEndCol", selEndCol + 1,
                          myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0) +
                          1);
    }
    else {
      Assert.assertTrue("has no selection", !myEditor.getSelectionModel().hasSelection());
    }
  }

  private static String stripTrailingSpaces(String actualText) {
    final Document document = EditorFactory.getInstance().createDocument(actualText);
    ((DocumentEx)document).stripTrailingSpaces(false);
    actualText = document.getText();
    return actualText;
  }

}
