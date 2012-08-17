package com.intellij.util.xml.stubs;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtenderEP;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xml.stubs.model.Bar;
import com.intellij.util.xml.stubs.model.Custom;
import org.jetbrains.annotations.NotNull;

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
                      "      Attribute:string:xxx\n" +
                      "      Attribute:int:666\n" +
                      "    Element:bar\n");
  }

  public void testDomExtension() throws Exception {
    DomExtenderEP ep = new DomExtenderEP();
    ep.domClassName = Bar.class.getName();
    ep.extenderClassName = TestExtender.class.getName();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), DomExtenderEP.EP_NAME, ep, myTestRootDisposable);

    doTest("extender.xml", "File:foo\n" +
                           "  Element:foo\n" +
                           "    Element:bar\n" +
                           "      Attribute:extend:xxx\n" +
                           "    Element:bar\n");
  }

  public static class TestExtender extends DomExtender<Bar> {

    @Override
    public void registerExtensions(@NotNull Bar bar, @NotNull DomExtensionsRegistrar registrar) {
      registrar.registerAttributeChildExtension(new XmlName("extend"), Custom.class);
    }
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
