package com.intellij.util.xml.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;

/**
 * @author Dmitry Avdeev
 *         Date: 8/3/12
 */
public class DomStubBuilderTest extends DomStubTest {

  public void testDomLoading() throws Exception {
    getRootStub("foo.xml");
  }

  public void testFoo() throws Exception {
    doTest("foo.xml", "File:foo\n" +
                      "  Element:foo\n" +
                      "    Element:bar\n" +
                      "      Attribute:attribute:xxx\n" +
                      "    Element:bar\n");
  }

  private ElementStub getRootStub(String filePath) {
    PsiFile psiFile = myFixture.configureByFile(filePath);

    StubTreeLoader loader = StubTreeLoader.getInstance();
    VirtualFile file = psiFile.getVirtualFile();
    assertTrue(loader.canHaveStub(file));
    ObjectStubTree stubTree = loader.readFromVFile(getProject(), file);
    assertNotNull(stubTree);
    ElementStub root = (ElementStub)stubTree.getRoot();
    assertNotNull(root);
    return root;
  }

  private void doTest(String file, String stubText) {
    ElementStub stub = getRootStub(file);
    assertEquals(stubText, DebugUtil.stubTreeToString(stub));
  }
}
