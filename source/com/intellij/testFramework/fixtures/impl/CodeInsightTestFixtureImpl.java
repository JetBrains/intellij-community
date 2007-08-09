/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInsight.daemon.impl.PostHighlightingPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private LocalInspectionTool[] myInspections;
  private Map<String, LocalInspectionTool> myAvailableTools = new THashMap<String, LocalInspectionTool>();
  private Map<String, LocalInspectionToolWrapper> myAvailableLocalTools = new THashMap<String, LocalInspectionToolWrapper>();

  private final TempDirTestFixture myTempDirFixture = new TempDirTextFixtureImpl();
  private final IdeaProjectTestFixture myProjectFixture;
  @NonNls private static final String XXX = "XXX";

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

  public void enableInspections(LocalInspectionTool... inspections) {
    myInspections = inspections;
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
  }

  public long testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               final String... filePaths) throws Throwable {

    final Ref<Long> duration = new Ref<Long>();
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        configureByFiles(filePaths);
        collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, duration);
      }
    }.execute().throwException();
    return duration.get().longValue();
  }

  public long testHighlighting(final String... filePaths) throws Throwable {
    return testHighlighting(true, true, true, filePaths);
  }

  @Nullable
  public PsiReference getReferenceAtCaretPosition(final String filePath) throws Throwable {
    final RunResult<PsiReference> runResult = new WriteCommandAction<PsiReference>(myProjectFixture.getProject()) {
      protected void run(final Result<PsiReference> result) throws Throwable {
        configureByFiles(filePath);
        final int offset = myEditor.getCaretModel().getOffset();
        final PsiReference psiReference = getFile().findReferenceAt(offset);
        result.setResult(psiReference);
      }
    }.execute();
    runResult.throwException();
    return runResult.getResultObject();
  }

  @NotNull
  public PsiReference getReferenceAtCaretPositionWithAssertion(final String filePath) throws Throwable {
    final PsiReference reference = getReferenceAtCaretPosition(filePath);
    assert reference != null: "no reference found at " + myEditor.getCaretModel().getLogicalPosition();
    return reference;
  }

  @NotNull
  public List<IntentionAction> getAvailableIntentions(final String... filePaths) throws Throwable {
    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();
    final Project project = myProjectFixture.getProject();
    new WriteCommandAction.Simple(project) {

      protected void run() throws Throwable {
        final int offset = configureByFiles(filePaths);

        final Collection<HighlightInfo> infos = doHighlighting();
        for (HighlightInfo info :infos) {
          if (info.quickFixActionRanges != null) {
            for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
              if (offset > 0 && !pair.getSecond().contains(offset)) {
                continue;
              }
              final HighlightInfo.IntentionActionDescriptor actionDescriptor = pair.first;
              final IntentionAction action = actionDescriptor.getAction();
              if (action.isAvailable(project, myEditor, myFile)) {
                availableActions.add(action);
                final List<IntentionAction> actions = actionDescriptor.getOptions(myFile.findElementAt(myEditor.getCaretModel().getOffset()));
                if (actions != null) {
                  for (IntentionAction intentionAction : actions) {
                    if (intentionAction.isAvailable(project, myEditor, myFile)) {
                      availableActions.add(intentionAction);
                    }
                  }
                }
              }
            }
          }
        }

        final IntentionAction[] intentionActions = IntentionManager.getInstance().getIntentionActions();
        for (IntentionAction intentionAction : intentionActions) {
          if (intentionAction.isAvailable(getProject(), getEditor(), getFile())) {
            availableActions.add(intentionAction);
          }
        }
        
      }
    }.execute().throwException();

    return availableActions;
  }

  public void launchAction(final IntentionAction action) throws Throwable {
    new WriteCommandAction(myProjectFixture.getProject()) {
      protected void run(final Result result) throws Throwable {
        action.invoke(getProject(), getEditor(), getFile());
      }
    }.execute().throwException();

  }

  public void testCompletion(final String[] filesBefore, final String fileAfter) throws Throwable {
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        configureByFiles(filesBefore);
        new CodeCompletionHandler().invoke(getProject(), myEditor, myFile);
        checkResultByFile(fileAfter, myFile, false);
      }
    }.execute().throwException();
  }

  public void testCompletion(String fileBefore, String fileAfter) throws Throwable {
    testCompletion(new String[] { fileBefore }, fileAfter);
  }

  public void testCompletionVariants(final String fileBefore, final String... items) throws Throwable {
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        configureByFiles(fileBefore);
        final Ref<LookupItem[]> myItems = Ref.create(null);
        new CodeCompletionHandler(){
          protected Lookup showLookup(Project project,
                                      Editor editor,
                                      LookupItem[] items,
                                      String prefix,
                                      LookupData data, PsiFile file) {
            myItems.set(items);
            return null;
          }

        }.invoke(getProject(), myEditor, myFile);
        final LookupItem[] items1 = myItems.get();
        UsefulTestCase.assertNotNull(items1);
        checkResultByFile(fileBefore, myFile, false);
        UsefulTestCase.assertSameElements(ContainerUtil.map(items1, new Function<LookupItem, String>() {
          public String fun(final LookupItem lookupItem) {
            return lookupItem.getLookupString();
          }
        }), items);
      }
    }.execute().throwException();
  }

  public void testRename(final String fileBefore, final String fileAfter, final String newName) throws Throwable {
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {
      protected void run() throws Throwable {
        configureByFiles(fileBefore);
        PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
        assert element != null: "element not found at caret position, offset " + myEditor.getCaretModel().getOffset();
        new RenameProcessor(myProjectFixture.getProject(), element, newName, false, false).run();
        checkResultByFile(fileAfter, myFile, false);
      }
    }.execute().throwException();
  }

  public void checkResultByFile(final String filePath) throws Throwable {
    new WriteCommandAction.Simple(myProjectFixture.getProject()) {

      protected void run() throws Throwable {
        checkResultByFile(filePath, myFile, false);
      }
    }.execute().throwException();
  }

  public void checkResultByFile(final String filePath, final String expectedFile, final boolean ignoreWhitespaces) throws Throwable {

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

    final String testDataPath = getTestDataPath();
    if (testDataPath != null) {
      FileUtil.copyDir(new File(testDataPath), new File(getTempDirPath()), false);
    }
    myProjectFixture.setUp();
    myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
    if (myInspections != null) {
      configureInspections(myInspections);
    }
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
        final LocalInspectionTool localInspectionTool = myAvailableTools.get(key.toString());
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
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      editorManager.closeFile(openFile);
    }

    myProjectFixture.tearDown();
    myTempDirFixture.tearDown();

    super.tearDown();
  }

  private int configureByFiles(@NonNls String... filePaths) throws IOException {
    myFile = null;
    myEditor = null;
    int offset = -1;
    for (String filePath : filePaths) {
      int fileOffset = configureByFileInner(filePath);
      if (fileOffset > 0) {
        offset = fileOffset;
      }
    }
    return offset;
  }

  public void configureByFile(final String file) throws IOException {
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Throwable {
        configureByFileInner(file);

      }
    }.execute();
  }
  /**
   *
   * @param filePath
   * @return caret offset or -1 if caret marker does not present
   * @throws IOException
   */
  private int configureByFileInner(@NonNls String filePath) throws IOException {
    String fullPath = getTempDirPath() + "/" + filePath;

    final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assert copy != null: "file " + fullPath + " not found";

    SelectionAndCaretMarkupLoader loader = new SelectionAndCaretMarkupLoader(copy.getPath());
    try {
      final OutputStream outputStream = copy.getOutputStream(null, 0, 0);
      outputStream.write(loader.newFileText.getBytes());
      outputStream.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (myFile == null) myFile = myPsiManager.findFile(copy);
    int offset = -1;
    if (myEditor == null) {
      myEditor = createEditor(copy);
      assert myEditor != null;
      if (loader.caretMarker != null) {
        offset = loader.caretMarker.getStartOffset();
        myEditor.getCaretModel().moveToOffset(offset);
      }
      if (loader.selStartMarker != null && loader.selEndMarker != null) {
        myEditor.getSelectionModel().setSelection(loader.selStartMarker.getStartOffset(), loader.selEndMarker.getStartOffset());
      }
    }
    return offset;
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

  @NotNull
  private Collection<HighlightInfo> collectAndCheckHighlightings(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, Ref<Long> duration)
    throws Exception {
    final Project project = getProject();
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord(XXX, UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(project), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    myPsiManager.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    final long start = System.currentTimeMillis();
//    ProfilingUtil.startCPUProfiling();
    Collection<HighlightInfo> infos = doHighlighting();
    duration.set(System.currentTimeMillis() - start);
//    ProfilingUtil.captureCPUSnapshot("testing");

    myPsiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());

    return infos;
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
    if (myAvailableTools.size() > 0) {
      LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(myFile, myEditor.getDocument(), 0, myFile.getTextLength());
      inspectionsPass.doCollectInformation(new MockProgressIndicator());
      highlights3 = inspectionsPass.getHighlights();
    }

    ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights1) {
      list.add(info);
    }

    for (HighlightInfo info : highlights2) {
      list.add(info);
    }

    if (highlights3 != null) {
      for (HighlightInfo info : highlights3) {
        list.add(info);
      }
    }

    return list;
  }

  private String getTestDataPath() {
    return myTestDataPath;
  }

  public Project getProject() {
    return myProjectFixture.getProject();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }

  static class SelectionAndCaretMarkupLoader {
    final String newFileText;
    final RangeMarker caretMarker;
    final RangeMarker selStartMarker;
    final RangeMarker selEndMarker;

    SelectionAndCaretMarkupLoader(String fullPath) throws IOException {
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
      assert vFile != null: "Cannot find file " + fullPath;
      vFile.refresh(false, false);
      String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
      Document document = EditorFactory.getInstance().createDocument(fileText);

      int caretIndex = fileText.indexOf(CARET_MARKER);
      int selStartIndex = fileText.indexOf(SELECTION_START_MARKER);
      int selEndIndex = fileText.indexOf(SELECTION_END_MARKER);

      caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      selStartMarker = selStartIndex >= 0 ? document.createRangeMarker(selStartIndex, selStartIndex) : null;
      selEndMarker = selEndIndex >= 0 ? document.createRangeMarker(selEndIndex, selEndIndex) : null;

      if (caretMarker != null) {
        document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_MARKER.length());
      }
      if (selStartMarker != null) {
        document.deleteString(selStartMarker.getStartOffset(), selStartMarker.getStartOffset() + SELECTION_START_MARKER.length());
      }
      if (selEndMarker != null) {
        document.deleteString(selEndMarker.getStartOffset(), selEndMarker.getStartOffset() + SELECTION_END_MARKER.length());
      }

      newFileText = document.getText();
    }

  }
  private void checkResultByFile(@NonNls String expectedFile,
                                   @NotNull PsiFile originalFile,
                                   boolean stripTrailingSpaces) throws IOException {

    Project project = myProjectFixture.getProject();

    project.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    if (stripTrailingSpaces) {
      ((DocumentEx)myEditor.getDocument()).stripTrailingSpaces(false);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    SelectionAndCaretMarkupLoader loader = new SelectionAndCaretMarkupLoader(getTestDataPath() + "/" + expectedFile);
    String newFileText1 = loader.newFileText;
    if (stripTrailingSpaces) {
      Document document1 = EditorFactory.getInstance().createDocument(loader.newFileText);
      ((DocumentEx)document1).stripTrailingSpaces(false);
      newFileText1 = document1.getText();
    }

    String text = originalFile.getText();
    text = StringUtil.convertLineSeparators(text, "\n");

    TestCase.assertEquals( "Text mismatch in file " + expectedFile, newFileText1, text );

    if (loader.caretMarker != null) {
      int caretLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.caretMarker.getStartOffset());
      int caretCol = loader.caretMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, caretLine, 0);

      TestCase.assertEquals("caretLine", caretLine + 1, myEditor.getCaretModel().getLogicalPosition().line + 1);
      TestCase.assertEquals("caretColumn", caretCol + 1, myEditor.getCaretModel().getLogicalPosition().column + 1);
    }

    if (loader.selStartMarker != null && loader.selEndMarker != null) {
      int selStartLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selStartMarker.getStartOffset());
      int selStartCol = loader.selStartMarker.getStartOffset() - StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0);

      int selEndLine = StringUtil.offsetToLineNumber(loader.newFileText, loader.selEndMarker.getEndOffset());
      int selEndCol = loader.selEndMarker.getEndOffset() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0);

      TestCase.assertEquals("selectionStartLine", selStartLine + 1,
                            StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionStart()) + 1);

      TestCase.assertEquals("selectionStartCol", selStartCol + 1, myEditor.getSelectionModel().getSelectionStart() -
                                                                  StringUtil.lineColToOffset(loader.newFileText, selStartLine, 0) + 1);

      TestCase.assertEquals("selectionEndLine", selEndLine + 1,
                            StringUtil.offsetToLineNumber(loader.newFileText, myEditor.getSelectionModel().getSelectionEnd()) + 1);

      TestCase.assertEquals("selectionEndCol", selEndCol + 1,
                            myEditor.getSelectionModel().getSelectionEnd() - StringUtil.lineColToOffset(loader.newFileText, selEndLine, 0) + 1);
    }
    else {
      TestCase.assertTrue("has no selection", !myEditor.getSelectionModel().hasSelection());
    }
  }

}
