package com.intellij.util.xml.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.impl.DomManagerImpl;

/**
 * @author Dmitry Avdeev
 *         Date: 8/3/12
 */
public class DomStubsTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testDomLoading() throws Exception {

    PsiFile psiFile = myFixture.configureByFile("foo.xml");

    StubTreeLoader loader = StubTreeLoader.getInstance();
    VirtualFile file = psiFile.getVirtualFile();
    assertTrue(loader.canHaveStub(file));
    ObjectStubTree stubTree = loader.readFromVFile(getProject(), file);
    assertNotNull(stubTree);
  }

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public DomStubsTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  private static final DomFileDescription<Foo> DOM_FILE_DESCRIPTION = new DomFileDescription<Foo>(Foo.class, "foo") {
    @Override
    public boolean hasStubs() {
      return true;
    }
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((DomManagerImpl)DomManager.getDomManager(getProject())).registerFileDescription(DOM_FILE_DESCRIPTION, getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/xml/dom-tests/testData/stubs";
  }
}
