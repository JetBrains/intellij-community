// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.inspections.MavenModelInspection;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public abstract class MavenDomTestCase extends MavenMultiVersionImportingTestCase {
  protected CodeInsightTestFixture myFixture;
  private final Map<VirtualFile, Long> myConfigTimestamps = new HashMap<>();
  private boolean myOriginalAutoCompletion;

  protected static final Function<LookupElement, String> RENDERING_TEXT = li -> {
    LookupElementPresentation presentation = new LookupElementPresentation();
    li.renderElement(presentation);
    return presentation.getItemText();
  };

  protected static final Function<LookupElement, String> LOOKUP_STRING = LookupElement::getLookupString;

  @Override
  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).getFixture();

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture);
    myFixture.setUp();
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true); // org.jetbrains.idea.maven.utils.MavenRehighlighter

    myFixture.enableInspections(MavenModelInspection.class);

    myOriginalAutoCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
  }

  @Override
  protected void tearDownFixtures() throws Exception {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = myOriginalAutoCompletion;
      myConfigTimestamps.clear();

      myFixture.tearDown();
    }
    finally {
      myFixture = null;
    }
  }

  protected PsiFile findPsiFile(VirtualFile f) {
    return PsiManager.getInstance(myProject).findFile(f);
  }

  protected void configureProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    VirtualFile file = createProjectPom(xml);
    configTest(file);
  }

  protected void configTest(VirtualFile f) {
    if (Comparing.equal(myConfigTimestamps.get(f), f.getTimeStamp())) return;
    myFixture.configureFromExistingVirtualFile(f);
    myConfigTimestamps.put(f, f.getTimeStamp());
  }

  protected void type(VirtualFile f, char c) {
    configTest(f);
    myFixture.type(c);
  }

  protected PsiReference getReferenceAtCaret(VirtualFile f) {
    configTest(f);
    return findPsiFile(f).findReferenceAt(getEditorOffset(f));
  }

  protected PsiReference getReferenceAt(VirtualFile f, int offset) {
    configTest(f);
    return findPsiFile(f).findReferenceAt(offset);
  }

  protected PsiElement getElementAtCaret(VirtualFile f) {
    configTest(f);
    return findPsiFile(f).findElementAt(getEditorOffset(f));
  }

  protected Editor getEditor() {
    return getEditor(myProjectPom);
  }

  protected Editor getEditor(VirtualFile f) {
    configTest(f);
    return myFixture.getEditor();
  }

  protected int getEditorOffset() {
    return getEditorOffset(myProjectPom);
  }

  protected int getEditorOffset(VirtualFile f) {
    return getEditor(f).getCaretModel().getOffset();
  }

  protected PsiFile getTestPsiFile() {
    return getTestPsiFile(myProjectPom);
  }

  private PsiFile getTestPsiFile(VirtualFile f) {
    configTest(f);
    return myFixture.getFile();
  }

  protected XmlTag findTag(String path) {
    return findTag(myProjectPom, path);
  }

  protected XmlTag findTag(VirtualFile file, String path) {
    return findTag(file, path, MavenDomProjectModel.class);
  }

  protected XmlTag findTag(VirtualFile file, String path, Class<? extends MavenDomElement> clazz) {
    MavenDomElement model = MavenDomUtil.getMavenDomModel(myProject, file, clazz);
    assertNotNull("Model is not of " + clazz, model);
    return MavenDomUtil.findTag(model, path);
  }

  protected void assertNoReferences(VirtualFile file, Class refClass) {
    PsiReference ref = getReferenceAtCaret(file);
    if (ref == null) return;
    PsiReference[] refs = ref instanceof PsiMultiReference ? ((PsiMultiReference)ref).getReferences() : new PsiReference[]{ref};
    for (PsiReference each: refs) {
      assertFalse(each.toString(), refClass.isInstance(each));
    }
  }

  protected void assertUnresolved(VirtualFile file) {
    PsiReference ref = getReferenceAtCaret(file);
    assertNotNull(ref);
    assertNull(ref.resolve());
  }

  protected void assertUnresolved(VirtualFile file, String expectedText) {
    PsiReference ref = getReferenceAtCaret(file);
    assertNotNull(ref);
    assertNull(ref.resolve());
    assertEquals(expectedText, ref.getCanonicalText());
  }

  protected void assertResolved(VirtualFile file, @NotNull PsiElement expected) throws IOException {
    doAssertResolved(file, expected);
  }

  @Nullable
  protected PsiReference getReference(VirtualFile file, @NotNull String referenceText) throws IOException {
    String text = VfsUtilCore.loadText(file);
    int index = text.indexOf(referenceText);
    assert index >= 0;

    assert text.indexOf(referenceText, index + referenceText.length()) == -1 : "Reference text '" +
                                                                               referenceText +
                                                                               "' occurs more than one times";

    return getReferenceAt(file, index);
  }

  @Nullable
  protected PsiReference getReference(VirtualFile file, @NotNull String referenceText, int index) throws IOException {
    String text = VfsUtilCore.loadText(file);
    int k = -1;

    do {
      k = text.indexOf(referenceText, k + 1);
      assert k >= 0 : index;
    }
    while (--index >= 0);

    return getReferenceAt(file, k);
  }

  @Nullable
  protected PsiElement resolveReference(VirtualFile file, @NotNull String referenceText) throws IOException {
    PsiReference ref = getReference(file, referenceText);
    assertNotNull(ref);

    PsiElement resolved = ref.resolve();
    if (resolved instanceof MavenPsiElementWrapper) {
      resolved = ((MavenPsiElementWrapper)resolved).getWrappee();
    }

    return resolved;
  }

  protected void assertResolved(VirtualFile file, @NotNull PsiElement expected, String expectedText) throws IOException {
    PsiReference ref = doAssertResolved(file, expected);
    assertEquals(expectedText, ref.getCanonicalText());
  }

  private PsiReference doAssertResolved(VirtualFile file, PsiElement expected) {
    assertNotNull("expected reference is null", expected);

    PsiReference ref = getReferenceAtCaret(file);
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    if (resolved instanceof MavenPsiElementWrapper) {
      resolved = ((MavenPsiElementWrapper)resolved).getWrappee();
    }
    assertEquals(expected, resolved);
    return ref;
  }

  protected void assertCompletionVariants(VirtualFile f, String... expected) {
    assertCompletionVariants(f, LOOKUP_STRING, expected);
  }

  protected void assertCompletionVariants(VirtualFile f, Function<? super LookupElement, String> lookupElementStringFunction, String... expected) {
    List<String> actual = getCompletionVariants(f, lookupElementStringFunction);
    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected void assertCompletionVariants(CodeInsightTestFixture f, Function<? super LookupElement, String> lookupElementStringFunction, String... expected) {
    List<String> actual = getCompletionVariants(f, lookupElementStringFunction);
    assertNotEmpty(actual);
    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected void assertCompletionVariantsInclude(VirtualFile f,
                                                 String... expected) {
    assertCompletionVariantsInclude(f, LOOKUP_STRING, expected);
  }

  protected void assertCompletionVariantsInclude(VirtualFile f,
                                                 Function<LookupElement, String> lookupElementStringFunction,
                                                 String... expected) {
    assertContain(getCompletionVariants(f, lookupElementStringFunction), expected);
  }

  protected void assertDependencyCompletionVariantsInclude(VirtualFile f, String... expected) {
    assertContain(getDependencyCompletionVariants(f), expected);
  }

  protected void assertCompletionVariantsDoNotInclude(VirtualFile f, String... expected) {
    assertDoNotContain(getCompletionVariants(f), expected);
  }

  protected List<String> getCompletionVariants(VirtualFile f) {
    return getCompletionVariants(f, li -> li.getLookupString());
  }

  protected List<String> getCompletionVariants(VirtualFile f, Function<? super LookupElement, String> lookupElementStringFunction) {
    configTest(f);
    LookupElement[] variants = myFixture.completeBasic();

    List<String> result = new ArrayList<>();
    for (LookupElement each : variants) {
      result.add(lookupElementStringFunction.apply(each));
    }
    return result;
  }

  protected Set<String> getDependencyCompletionVariants(VirtualFile f) {
    return getDependencyCompletionVariants(f, it -> MavenDependencyCompletionUtil.getPresentableText(it));
  }

  protected Set<String> getDependencyCompletionVariants(VirtualFile f,
                                                        Function<? super MavenRepositoryArtifactInfo, String> lookupElementStringFunction) {
    configTest(f);
    LookupElement[] variants = myFixture.completeBasic();

    Set<String> result = new TreeSet<>();
    for (LookupElement each : variants) {
      var object = each.getObject();
      if (object instanceof MavenRepositoryArtifactInfo info) {
        result.add(lookupElementStringFunction.apply(info));
      }
    }
    return result;
  }

  @Nullable
  protected List<String> getCompletionVariants(CodeInsightTestFixture fixture,
                                               Function<? super LookupElement, String> lookupElementStringFunction) {
    LookupElement[] variants = fixture.getLookupElements();
    if (variants == null) return null;

    List<String> result = new ArrayList<>();
    for (LookupElement each : variants) {
      result.add(lookupElementStringFunction.apply(each));
    }
    return result;
  }

  protected void assertDocumentation(String expectedText) {
    PsiElement originalElement = getElementAtCaret(myProjectPom);
    PsiElement targetElement = DocumentationManager.getInstance(myProject)
                                                   .findTargetElement(getEditor(), getTestPsiFile(), originalElement);

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(targetElement);
    assertEquals(expectedText, provider.generateDoc(targetElement, originalElement));

    // should work for lookup as well as for tags
    PsiElement lookupElement = provider.getDocumentationElementForLookupItem(PsiManager.getInstance(myProject),
                                                                             originalElement.getText(),
                                                                             originalElement);
    assertSame(targetElement, lookupElement);
  }

  protected void checkHighlighting() {
    checkHighlighting(myProjectPom);
  }

  protected void checkHighlighting(VirtualFile f) {
    checkHighlighting(f, true, false, true);
  }

  protected void checkHighlighting(VirtualFile f, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    configTest(myProjectPom);
    try {
      myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, f);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  protected IntentionAction getIntentionAtCaret(String intentionName) {
    return getIntentionAtCaret(myProjectPom, intentionName);
  }

  protected IntentionAction getIntentionAtCaret(VirtualFile pomFile, String intentionName) {
    configTest(pomFile);
    try {
      List<IntentionAction> intentions = myFixture.getAvailableIntentions();

      return CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  protected void assertRenameResult(String value, String expectedXml) throws Exception {
    doRename(myProjectPom, value);
    assertEquals(createPomXml(expectedXml), getTestPsiFile(myProjectPom).getText());
  }

  protected void doRename(final VirtualFile f, String value) {
    final MapDataContext context = createRenameDataContext(f, value);
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    assertNotNull(renameHandler);

    invokeRename(context, renameHandler);
  }

  protected void doInlineRename(final VirtualFile f, String value) {
    final MapDataContext context = createRenameDataContext(f, value);
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    assertNotNull(renameHandler);
    assertInstanceOf(renameHandler, VariableInplaceRenameHandler.class);
    CodeInsightTestUtil.doInlineRename((VariableInplaceRenameHandler)renameHandler, value, myFixture);
  }

  protected void assertCannotRename() {
    MapDataContext context = createRenameDataContext(myProjectPom, "new name");
    RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    if (handler == null) return;
    try {
      invokeRename(context, handler);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      if (!e.getMessage().startsWith("Cannot perform refactoring.")) {
        throw e;
      }
    }
  }

  private void invokeRename(final MapDataContext context, final RenameHandler renameHandler) {
    renameHandler.invoke(myProject, PsiElement.EMPTY_ARRAY, context);
  }

  private MapDataContext createDataContext(VirtualFile f) {
    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.EDITOR, getEditor(f));
    context.put(CommonDataKeys.PSI_FILE, getTestPsiFile(f));
    context.put(CommonDataKeys.PSI_ELEMENT, TargetElementUtil.findTargetElement(getEditor(f),
                                                                                TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                                                                                | TargetElementUtil.ELEMENT_NAME_ACCEPTED));
    return context;
  }

  private MapDataContext createRenameDataContext(VirtualFile f, String value) {
    MapDataContext context = createDataContext(f);
    context.put(PsiElementRenameHandler.DEFAULT_NAME, value);
    return context;
  }

  protected void assertSearchResults(VirtualFile file, PsiElement... expected) {
    assertUnorderedElementsAreEqual(search(file), expected);
  }

  protected void assertSearchResultsInclude(VirtualFile file, PsiElement... expected) {
    assertContain(search(file), expected);
  }

  protected List<PsiElement> search(VirtualFile file) {
    final MapDataContext context = createDataContext(file);
    UsageTarget[] targets = UsageTargetUtil.findUsageTargets(context::getData);
    PsiElement target = ((PsiElement2UsageTargetAdapter)targets[0]).getElement();
    List<PsiReference> result = new ArrayList<>(ReferencesSearch.search(target).findAll());
    return ContainerUtil.map(result, PsiReference::getElement);
  }

  protected void assertHighlighted(VirtualFile file, HighlightPointer... expected) {
    Editor editor = getEditor(file);
    HighlightUsagesHandler.invoke(myProject, editor, getTestPsiFile(file));

    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    List<HighlightPointer> actual = new ArrayList<>();
    for (RangeHighlighter each: highlighters) {
      if (!each.isValid()) continue;
      int offset = each.getStartOffset();
      PsiElement element = getTestPsiFile(file).findElementAt(offset);
      element = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      String text = editor.getDocument().getText().substring(offset, each.getEndOffset());
      actual.add(new HighlightPointer(element, text));
    }

    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected static class HighlightPointer {
    public PsiElement element;
    public String text;

    public HighlightPointer(PsiElement element, String text) {
      this.element = element;
      this.text = text;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HighlightPointer that = (HighlightPointer)o;

      if (element != null ? !element.equals(that.element) : that.element != null) return false;
      if (text != null ? !text.equals(that.text) : that.text != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = element != null ? element.hashCode() : 0;
      result = 31 * result + (text != null ? text.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "HighlightInfo{" +
             "element=" + element +
             ", text='" + text + '\'' +
             '}';
    }
  }
}