package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public class XmlFindUsagesTest extends JavaCodeInsightTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  private static final String BASE_PATH = "/codeInsight/xmlFindUsages/";
  private String myOldDoctype;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // todo: support find usages of html5 tags (need to support find usages of RelaxNG elements)
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myOldDoctype != null) {
        ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean clearModelBeforeConfiguring() {
    return "testFindTagUsage".equals(getName());
  }

  public void testFindTagUsage() throws Exception {
    PsiReference[] references;
    references = findElementAtCaret(new String[]{BASE_PATH + "1.html"});
    //"Simple tag find"
    assertSize(4, references);

    references = findElementAtCaret(new String[]{BASE_PATH+"3.html", BASE_PATH+"3_2.html"});
    //"Tag find in two files"
    assertSize(10, references);

    final String location = "4.xsd";
    final String url = "http://aaa.bbb";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, getTestRootDisposable());

    references = findElementAtCaret(new String[]{BASE_PATH+location, BASE_PATH+"4.xml"});
    assertSize(5, references);

    references = findElementAtCaret(new String[]{BASE_PATH+"5.xml"});
    assertSize(6, references);
  }

  public void testFindTagUsageInDtdIncludedViaEntity() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "6.xml", BASE_PATH + "6.dtd"});
    // "Find tag declared in entity"
    assertSize(6, references);

    final PsiReference referenceAt = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    assertNotNull(referenceAt);
    final PsiElement object = referenceAt.resolve();
    assertNotNull(object);
    assertTrue(((Navigatable)object).canNavigate());
    assertEquals("6.dtd", object.getOriginalElement().getContainingFile().getVirtualFile().getName());
  }

  public void testFindTagUsageInEntity() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "7.xml"});
    // "Find tag used in entity"
    assertSize(4, references);
  }

  public void testFindEntityUsages() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "8.xml"});
    // "Find usages in entity"
    assertSize(3, references);
  }

  public void testFindNamedObjectsInSchema() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "9.xsd"});
    // "Find complex type in schema"
    assertSize(3, references);

    references = findElementAtCaret(new String[]{BASE_PATH + "9_2.xsd"});
    // "Find simple type in schema"
    assertSize(4, references);

    references = findElementAtCaret(new String[]{BASE_PATH + "9_3.xsd"});
    // "Find group in schema"
    assertSize(2, references);

    references = findElementAtCaret(new String[]{BASE_PATH + "9_4.xsd"});
    // "Find attribute grup in schema"
    assertSize(3, references);
  }

  public void testFindAttributeUsage() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "2.html"});
    // "Simple attribute find"
    assertSize(3, references);
  }

  public void testFindAttributeUsageInSchema() throws Exception {
    PsiReference[] references = findElementAtCaret(new String[]{BASE_PATH + "10.xsd"});
    // "Find attribute from ref in schema"
    assertSize(2, references);
  }

  private PsiReference[] findElementAtCaret(String[] files) throws Exception {
    final VirtualFile[] vfiles = new VirtualFile[files.length];

    for (int i = 0; i < files.length; i++) {
      vfiles[i] = findVirtualFile(files[i]);

    }
    configureByFiles(null, vfiles);

    PsiElement referenceTo = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    if (referenceTo == null) return PsiReference.EMPTY_ARRAY;
    assertTrue("Cannot find element in caret", LanguageFindUsages.canFindUsagesFor(referenceTo));

    return ReferencesSearch.search(referenceTo, GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
  }
}