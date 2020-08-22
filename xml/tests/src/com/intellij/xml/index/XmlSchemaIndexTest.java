// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"ConstantConditions"})
public class XmlSchemaIndexTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String NS = "http://java.jb.com/xml/ns/javaee";

  private static @NotNull Collection<String> computeTagNames(@NotNull VirtualFile file) throws IOException {
    List<String> tags = new ArrayList<>();
    XmlTagNamesIndex.computeTagNames(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), s -> tags.add(s));
    return tags;
  }

  public void testBuilder() throws IOException {
    VirtualFile file = myFixture.copyFileToProject("spring-beans-2.0.xsd");
    final Collection<String> tags = computeTagNames(file);
    assertEquals(22, tags.size());

    final String ns = XsdNamespaceBuilder.computeNamespace(file.getInputStream());
    assertEquals("http://www.springframework.org/schema/beans", ns);

    final VirtualFile xsd = myFixture.copyFileToProject("XMLSchema.xsd");
    final String namespace = XsdNamespaceBuilder.computeNamespace(xsd.getInputStream());
    assertEquals("http://www.w3.org/2001/XMLSchema", namespace);

    final Collection<String> xstags = computeTagNames(xsd);
    assertEquals(69, xstags.size());
    assertTrue(xstags.contains("schema"));
  }

  public void testXsdNamespaceBuilder() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("web-app_2_5.xsd");
    final XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new InputStreamReader(file.getInputStream(),
                                                                                                   StandardCharsets.UTF_8));
    assertEquals(NS, builder.getNamespace());
    assertEquals("2.5", builder.getVersion());
    assertEquals(Collections.singletonList("web-app"), builder.getTags());
  }

  public void testRootTags() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("XMLSchema.xsd");
    final XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new InputStreamReader(file.getInputStream(),
                                                                                                   StandardCharsets.UTF_8));
    assertEquals(XmlUtil.XML_SCHEMA_URI, builder.getNamespace());
    assertEquals("1.0", builder.getVersion());
    assertEquals(Collections.singletonList("schema"), builder.getRootTags());
    assertEquals(41, builder.getTags().size());
  }

  public void testTagNameIndex() {
    myFixture.copyDirectoryToProject("", "");

    Project project = getProject();
    Collection<String> tags = XmlTagNamesIndex.getAllTagNames(project);
    assertTrue(tags.size() > 26);
    Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName("bean", project);
    assertEquals(1, files.size());
    Module module = ModuleUtilCore.findModuleForFile(files.iterator().next(), project);
    assert module != null;
    final Collection<VirtualFile> files1 = FileBasedIndex.getInstance().getContainingFiles(XmlTagNamesIndex.NAME, "web-app", module.getModuleContentScope());

    assertEquals(new ArrayList<>(files1).toString(), 2, files1.size());

    List<String> names = new ArrayList<>(ContainerUtil.map(files1, virtualFile -> virtualFile.getName()));
    Collections.sort(names);
    assertEquals(Arrays.asList("web-app_2_5.xsd", "web-app_3_0.xsd"), names);
  }

  public void testNamespaceIndex() {

    myFixture.copyDirectoryToProject("", "");

    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>> files =
      XmlNamespaceIndex.getResourcesByNamespace(NS,
                                                getProject(),
                                                getModule());
    assertEquals(2, files.size());

    IndexedRelevantResource<String, XsdNamespaceBuilder>
      resource = XmlNamespaceIndex.guessSchema(NS, "web-app", "3.0", null, getModule(), getProject());
    assertNotNull(resource);
    XsdNamespaceBuilder builder = resource.getValue();
    assertEquals(NS, builder.getNamespace());
    assertEquals("3.0", builder.getVersion());
    assertEquals(Collections.singletonList("web-app"), builder.getTags());

    resource = XmlNamespaceIndex.guessSchema(NS, "web-app", "2.5", null, getModule(), getProject());
    assertNotNull(resource);
    builder = resource.getValue();
    assertEquals(NS, builder.getNamespace());
    assertEquals("2.5", builder.getVersion());
    assertEquals(Collections.singletonList("web-app"), builder.getTags());

    resource = XmlNamespaceIndex.guessSchema(NS, "foo-bar", "2.5", null, getModule(), getProject());
    assertNull(resource);
  }

  public void testGuessDTD() {
    myFixture.copyDirectoryToProject("", "");
    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>> files =
      XmlNamespaceIndex.getResourcesByNamespace("foo.dtd",
                                                getProject(),
                                                getModule());
    assertEquals(2, files.size());

    PsiFile file = myFixture.configureByFile("foo.xml");
    assertTrue(XmlNamespaceIndex.guessDtd("foo://bar/1/foo.dtd", file).getVirtualFile().getPath().endsWith("/1/foo.dtd"));
    assertTrue(XmlNamespaceIndex.guessDtd("foo://bar/2/foo.dtd", file).getVirtualFile().getPath().endsWith("/2/foo.dtd"));
  }

  public void testGuessByLocation() {
    myFixture.copyDirectoryToProject("", "");
    String namespace = "http://www.liquibase.org/xml/ns/dbchangelog";
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources =
      XmlNamespaceIndex.getResourcesByNamespace(namespace, getProject(), getModule());
    assertEquals(2, resources.size());
    assertEquals("dbchangelog-3.3.xsd", XmlNamespaceIndex
      .guessSchema(namespace, null, null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.3.xsd", getModule(), getProject())
      .getFile().getName());
    assertEquals("dbchangelog-3.1.xsd", XmlNamespaceIndex
      .guessSchema(namespace, null, null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd", getModule(), getProject())
      .getFile().getName());
  }

  public void testNullSerialization() throws Exception {
    DataExternalizer<XsdNamespaceBuilder> externalizer = new XmlNamespaceIndex().getValueExternalizer();
    XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new StringReader(""));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    externalizer.save(new DataOutputStream(out), builder);

    XsdNamespaceBuilder read = externalizer.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
    assertEquals(read, builder);
    assertEquals(read.hashCode(), builder.hashCode());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/index";
  }
}
