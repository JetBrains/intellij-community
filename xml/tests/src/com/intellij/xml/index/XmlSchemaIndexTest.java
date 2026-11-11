// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"ConstantConditions"})
@TestApplication
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class XmlSchemaIndexTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/index");

  private static final String NS = "http://java.jb.com/xml/ns/javaee";

  private static @NotNull Collection<String> computeTagNames(@NotNull VirtualFile file) throws IOException {
    List<String> tags = new ArrayList<>();
    XmlTagNamesIndex.computeTagNames(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), s -> tags.add(s));
    return tags;
  }

  @Test
  public void testBuilder() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testXsdNamespaceBuilder() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.copyFileToProject("web-app_2_5.xsd");
    final XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new InputStreamReader(file.getInputStream(),
                                                                                                   StandardCharsets.UTF_8));
    assertEquals(NS, builder.getNamespace());
    assertEquals("2.5", builder.getVersion());
    assertEquals(Collections.singletonList("web-app"), builder.getTags());
  }

  @Test
  public void testRootTags() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.copyFileToProject("XMLSchema.xsd");
    final XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new InputStreamReader(file.getInputStream(),
                                                                                                   StandardCharsets.UTF_8));
    assertEquals(XmlUtil.XML_SCHEMA_URI, builder.getNamespace());
    assertEquals("1.0", builder.getVersion());
    assertEquals(Collections.singletonList("schema"), builder.getRootTags());
    assertEquals(41, builder.getTags().size());
  }

  @Test
  public void testTagNameIndex() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("", "");

    Project project = myFixture.getProject();
    ReadAction.runBlocking(() -> {
      Collection<String> tags = XmlTagNamesIndex.getAllTagNames(project);
      assertTrue(tags.size() > 26);
      Collection<VirtualFile> files = XmlTagNamesIndex.getFilesByTagName("bean", project);
      assertEquals(1, files.size());
      Module module = ModuleUtilCore.findModuleForFile(files.iterator().next(), project);
      assert module != null;
      final Collection<VirtualFile> files1 = FileBasedIndex.getInstance().getContainingFiles(XmlTagNamesIndex.NAME, "web-app", module.getModuleContentScope());

      assertEquals(2, files1.size(), new ArrayList<>(files1).toString());

      List<String> names = new ArrayList<>(ContainerUtil.map(files1, virtualFile -> virtualFile.getName()));
      Collections.sort(names);
      assertEquals(Arrays.asList("web-app_2_5.xsd", "web-app_3_0.xsd"), names);
    });
  }

  @Test
  public void testNamespaceIndex() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    myFixture.copyDirectoryToProject("", "");

    ReadAction.runBlocking(() -> {
      final List<IndexedRelevantResource<String, XsdNamespaceBuilder>> files =
        XmlNamespaceIndex.getResourcesByNamespace(NS,
                                                  myFixture.getProject(),
                                                  myFixture.getModule());
      assertEquals(2, files.size());

      IndexedRelevantResource<String, XsdNamespaceBuilder>
        resource = XmlNamespaceIndex.guessSchema(NS, "web-app", "3.0", null, myFixture.getModule(), myFixture.getProject());
      assertNotNull(resource);
      XsdNamespaceBuilder builder = resource.getValue();
      assertEquals(NS, builder.getNamespace());
      assertEquals("3.0", builder.getVersion());
      assertEquals(Collections.singletonList("web-app"), builder.getTags());

      resource = XmlNamespaceIndex.guessSchema(NS, "web-app", "2.5", null, myFixture.getModule(), myFixture.getProject());
      assertNotNull(resource);
      builder = resource.getValue();
      assertEquals(NS, builder.getNamespace());
      assertEquals("2.5", builder.getVersion());
      assertEquals(Collections.singletonList("web-app"), builder.getTags());

      resource = XmlNamespaceIndex.guessSchema(NS, "foo-bar", "2.5", null, myFixture.getModule(), myFixture.getProject());
      assertNull(resource);
    });
  }

  @Test
  public void testGuessDTD() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("", "");
    ReadAction.runBlocking(() -> {
      final List<IndexedRelevantResource<String, XsdNamespaceBuilder>> files =
        XmlNamespaceIndex.getResourcesByNamespace("foo.dtd",
                                                  myFixture.getProject(),
                                                  myFixture.getModule());
      assertEquals(2, files.size());
    });

    PsiFile file = myFixture.configureByFile("foo.xml");
    ReadAction.runBlocking(() -> {
      assertTrue(XmlNamespaceIndex.guessDtd("foo://bar/1/foo.dtd", file).getVirtualFile().getPath().endsWith("/1/foo.dtd"));
      assertTrue(XmlNamespaceIndex.guessDtd("foo://bar/2/foo.dtd", file).getVirtualFile().getPath().endsWith("/2/foo.dtd"));
    });
  }

  @Test
  public void testGuessByLocation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("", "");
    ReadAction.runBlocking(() -> {
      String namespace = "http://www.liquibase.org/xml/ns/dbchangelog";
      List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources =
        XmlNamespaceIndex.getResourcesByNamespace(namespace, myFixture.getProject(), myFixture.getModule());
      assertEquals(2, resources.size());
      assertEquals("dbchangelog-3.3.xsd", XmlNamespaceIndex
        .guessSchema(namespace, null, null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.3.xsd", myFixture.getModule(), myFixture.getProject())
        .getFile().getName());
      assertEquals("dbchangelog-3.1.xsd", XmlNamespaceIndex
        .guessSchema(namespace, null, null, "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd", myFixture.getModule(), myFixture.getProject())
        .getFile().getName());
    });
  }

  @Test
  public void testNullSerialization() throws Exception {
    DataExternalizer<XsdNamespaceBuilder> externalizer = new XmlNamespaceIndex().getValueExternalizer();
    XsdNamespaceBuilder builder = XsdNamespaceBuilder.computeNamespace(new StringReader(""));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    externalizer.save(new DataOutputStream(out), builder);

    XsdNamespaceBuilder read = externalizer.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
    assertEquals(read, builder);
    assertEquals(read.hashCode(), builder.hashCode());
  }
}
