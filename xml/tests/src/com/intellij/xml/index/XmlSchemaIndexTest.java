package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSchemaIndexTest extends CodeInsightFixtureTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public XmlSchemaIndexTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void testBuilder() throws IOException {

    VirtualFile file = myFixture.copyFileToProject("spring-beans-2.0.xsd");
    assert file != null;
    final Collection<String> tags = XsdTagNameBuilder.computeTagNames(file.getInputStream());
    assert tags != null;
    assertEquals(22, tags.size());

    final String ns = XsdNamespaceBuilder.computeNamespace(file.getInputStream());
    assertEquals("http://www.springframework.org/schema/beans", ns);

    final VirtualFile xsd = myFixture.copyFileToProject("XMLSchema.xsd");
    assert xsd != null;
    final String xsns = XsdNamespaceBuilder.computeNamespace(xsd.getInputStream());
    assertEquals("http://www.w3.org/2001/XMLSchema", xsns);

    final Collection<String> xstags = XsdTagNameBuilder.computeTagNames(xsd.getInputStream());
    assert xstags != null;
    assertEquals(69, xstags.size());
    assertTrue(xstags.contains("schema"));
  }

  public void testTagNameIndex() {

    myFixture.copyDirectoryToProject("", "");

    final Project project = getProject();
    final Collection<String> tags = XmlTagNamesIndex.getAllTagNames(project);
    assertTrue(tags.size() > 26);
    final Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName("bean", project);
    assertEquals(1, files.size());

    final Collection<VirtualFile> files1 = XmlTagNamesIndex.getFilesByTagName("schema", project);
    assertEquals(files1.toString(), 2, files1.size());

    Collection<String> names = ContainerUtil.map(files1, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getName();
      }
    });
    List<String> expected = Arrays.asList("XMLSchema.xsd", "XMLSchema.xsd");
    names.removeAll(expected);
    assertTrue(files1.toString(), names.isEmpty());
  }

  public void testNamespaceIndex() {

    myFixture.copyDirectoryToProject("", "");

    final List<IndexedRelevantResource<String, String>> files =
      XmlNamespaceIndex.getResourcesByNamespace("http://www.springframework.org/schema/beans",
                                                getProject(),
                                                myModule);
    assertEquals(1, files.size());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/index";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
