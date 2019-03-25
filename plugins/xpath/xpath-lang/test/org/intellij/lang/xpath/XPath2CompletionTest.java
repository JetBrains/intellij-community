package org.intellij.lang.xpath;

public class XPath2CompletionTest extends TestBase {
  public void testCastInsert() {
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    configure();
    assertContainsElements(myFixture.getLookupElementStrings(), "xs:anyAtomicType", "xs:untypedAtomic", "xs:anyURI");
  }

  private void configure() {
    myFixture.configureByFile(getTestFileName() + ".xpath2");
    myFixture.completeBasic();
  }

  public void testTreatInsert() {
    configure();
    myFixture.type("\n");
    myFixture.checkResultByFile(getTestFileName() + "_after.xpath2");
  }

  @Override
  protected String getSubPath() {
    return "xpath2/completion";
  }
}