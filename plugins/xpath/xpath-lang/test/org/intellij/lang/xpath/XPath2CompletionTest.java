package org.intellij.lang.xpath;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XPath2CompletionTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xpath2/completion"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
  }

  @Test
  public void testCastInsert() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestNamespaceContext.install(myFixture.getTestRootDisposable());
    configure();
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertTrue(strings.containsAll(List.of("xs:anyAtomicType", "xs:untypedAtomic", "xs:anyURI")), strings::toString);
  }

  private void configure() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testFileName + ".xpath2");
    myFixture.completeBasic();
  }

  @Test
  public void testTreatInsert() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure();
    myFixture.type("\n");
    myFixture.checkResultByFile(testFileName + "_after.xpath2");
  }
}