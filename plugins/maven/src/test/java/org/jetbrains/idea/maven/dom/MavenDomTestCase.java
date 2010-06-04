/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class MavenDomTestCase extends MavenImportingTestCase {
  protected CodeInsightTestFixture myFixture;
  private final Map<VirtualFile, Long> myConfigTimestamps = new THashMap<VirtualFile, Long>();
  private boolean myOriginalAutoCompletion;

  @Override
  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder().getFixture();

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture);
    myFixture.setUp();

    myFixture.enableInspections(MavenModelInspection.class);

    myOriginalAutoCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
  }

  @Override
  protected void tearDownFixtures() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = myOriginalAutoCompletion;
    myFixture.tearDown();
    myFixture = null;
    myConfigTimestamps.clear();
  }

  protected PsiFile findPsiFile(VirtualFile f) {
    return PsiManager.getInstance(myProject).findFile(f);
  }

  protected void configTest(VirtualFile f) throws IOException {
    if (Comparing.equal(myConfigTimestamps.get(f), f.getModificationStamp())) return;
    myFixture.configureFromExistingVirtualFile(f);
    myConfigTimestamps.put(f, f.getModificationStamp());
  }

  protected void type(VirtualFile f, char c) throws IOException {
    configTest(f);
    myFixture.type(c);
  }

  protected PsiReference getReferenceAtCaret(VirtualFile f) throws IOException {
    configTest(f);
    return findPsiFile(f).findReferenceAt(getEditorOffset(f));
  }

  protected PsiElement getElementAtCaret(VirtualFile f) throws IOException {
    configTest(f);
    return findPsiFile(f).findElementAt(getEditorOffset(f));
  }

  protected Editor getEditor() throws IOException {
    return getEditor(myProjectPom);
  }

  protected Editor getEditor(VirtualFile f) throws IOException {
    configTest(f);
    return myFixture.getEditor();
  }

  protected int getEditorOffset() throws IOException {
    return getEditorOffset(myProjectPom);
  }

  protected int getEditorOffset(VirtualFile f) throws IOException {
    return getEditor(f).getCaretModel().getOffset();
  }

  protected PsiFile getTestPsiFile() throws IOException {
    return getTestPsiFile(myProjectPom);
  }

  private PsiFile getTestPsiFile(VirtualFile f) throws IOException {
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

  protected void assertNoReferences(VirtualFile file, Class refClass) throws IOException {
    PsiReference ref = getReferenceAtCaret(file);
    if (ref == null) return;
    PsiReference[] refs = ref instanceof PsiMultiReference ? ((PsiMultiReference)ref).getReferences() : new PsiReference[]{ref};
    for (PsiReference each : refs) {
      assertFalse(each.toString(), refClass.isInstance(each));
    }
  }

  protected void assertUnresolved(VirtualFile file) throws IOException {
    PsiReference ref = getReferenceAtCaret(file);
    assertNotNull(ref);
    assertNull(ref.resolve());
  }

  protected void assertUnresolved(VirtualFile file, String expectedText) throws IOException {
    PsiReference ref = getReferenceAtCaret(file);
    assertNotNull(ref);
    assertNull(ref.resolve());
    assertEquals(expectedText, ref.getCanonicalText());
  }

  protected void assertResolved(VirtualFile file, PsiElement expected) throws IOException {
    doAssertResolved(file, expected);
  }

  protected void assertResolved(VirtualFile file, PsiElement expected, String expectedText) throws IOException {
    PsiReference ref = doAssertResolved(file, expected);
    assertEquals(expectedText, ref.getCanonicalText());
  }

  private PsiReference doAssertResolved(VirtualFile file, PsiElement expected) throws IOException {
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

  protected void assertCompletionVariants(VirtualFile f, String... expected) throws IOException {
    List<String> actual = getCompletionVariants(f);
    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected void assertCompletionVariantsInclude(VirtualFile f, String... expected) throws IOException {
    assertContain(getCompletionVariants(f), expected);
  }

  protected void assertCompletionVariantsDoNotInclude(VirtualFile f, String... expected) throws IOException {
    assertDoNotContain(getCompletionVariants(f), expected);
  }

  protected List<String> getCompletionVariants(VirtualFile f) throws IOException {
    configTest(f);
    LookupElement[] variants = myFixture.completeBasic();

    List<String> result = new ArrayList<String>();
    for (LookupElement each : variants) {
      result.add(each.getLookupString());
    }
    return result;
  }

  protected void assertDocumentation(String expectedText) throws IOException {
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

  protected void checkHighlighting() throws IOException {
    checkHighlighting(myProjectPom);
  }

  protected void checkHighlighting(VirtualFile f) throws IOException {
    checkHighlighting(f, true, true, true);
  }

  protected void checkHighlighting(VirtualFile f, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws IOException {
    configTest(myProjectPom);
    try {
      myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, f);
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  protected IntentionAction getIntentionAtCaret(String intentionName) throws IOException {
    configTest(myProjectPom);
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

  protected void doRename(final VirtualFile f, String value) throws IOException {
    final MapDataContext context = createRenameDataContext(f, value);
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    assertNotNull(renameHandler);

    invokeRename(context, renameHandler);
  }

  protected void assertCannotRename() throws Exception {
    MapDataContext context = createRenameDataContext(myProjectPom, "new name");
    RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    if (handler == null) return;
    try {
      invokeRename(context, handler);
    }
    catch (Exception e) {
      if (!e.getMessage().startsWith("Cannot perform refactoring.")) {
        throw e;
      }
    }
  }

  private void invokeRename(final MapDataContext context, final RenameHandler renameHandler) {
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        renameHandler.invoke(myProject, PsiElement.EMPTY_ARRAY, context);
      }
    }.execute();
  }

  private MapDataContext createDataContext(VirtualFile f) throws IOException {
    MapDataContext context = new MapDataContext();
    context.put(PlatformDataKeys.EDITOR, getEditor(f));
    context.put(LangDataKeys.PSI_FILE, getTestPsiFile(f));
    context.put(LangDataKeys.PSI_ELEMENT, TargetElementUtil.findTargetElement(getEditor(f),
                                                                              TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED
                                                                              | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED));
    return context;
  }

  private MapDataContext createRenameDataContext(VirtualFile f, String value) throws IOException {
    MapDataContext context = createDataContext(f);
    context.put(PsiElementRenameHandler.DEFAULT_NAME, value);
    return context;
  }

  protected void assertSearchResults(VirtualFile file, PsiElement... expected) throws IOException {
    assertUnorderedElementsAreEqual(search(file), expected);
  }

  protected void assertSearchResultsInclude(VirtualFile file, PsiElement... expected) throws IOException {
    assertContain(search(file), expected);
  }

  protected List<PsiElement> search(VirtualFile file) throws IOException {
    final MapDataContext context = createDataContext(file);
    UsageTarget[] targets = UsageTargetUtil.findUsageTargets(new DataProvider() {
      public Object getData(@NonNls String dataId) {
        return context.getData(dataId);
      }
    });
    PsiElement target = ((PsiElement2UsageTargetAdapter)targets[0]).getElement();
    List<PsiReference> result = new ArrayList<PsiReference>(ReferencesSearch.search(target).findAll());
    return ContainerUtil.map(result, new Function<PsiReference, PsiElement>() {
      public PsiElement fun(PsiReference psiReference) {
        return psiReference.getElement();
      }
    });
  }

  protected void assertHighlighted(VirtualFile file, HighlightInfo... expected) throws IOException {
    Editor editor = getEditor(file);
    HighlightUsagesHandler.invoke(myProject, editor, getTestPsiFile(file));

    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    List<HighlightInfo> actual = new ArrayList<HighlightInfo>();
    for (RangeHighlighter each : highlighters) {
      int offset = each.getStartOffset();
      PsiElement element = getTestPsiFile(file).findElementAt(offset);
      element = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      String text = editor.getDocument().getText().substring(offset, each.getEndOffset());
      actual.add(new HighlightInfo(element, text));
    }

    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected static class HighlightInfo {
    public PsiElement element;
    public String text;

    public HighlightInfo(PsiElement element, String text) {
      this.element = element;
      this.text = text;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HighlightInfo that = (HighlightInfo)o;

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
  }
}