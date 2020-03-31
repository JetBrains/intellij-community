// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.resolve;

import com.intellij.codeInsight.completion.TagNameReferenceCompletionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.JavaResolveTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.xml.util.XmlUtil;

import java.io.File;

@HeavyPlatformTestCase.WrapInCommand
public class XmlResolveTest extends JavaResolveTestCase {
  public void testDtdDescriptor1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlElementDecl);
  }

  public void testEmptyNamespaces() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlElementDecl);
  }

  public void testOuterDtdDescriptor1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();

    assertTrue(target instanceof XmlElementDecl);
    CollectConsumer<LookupElement> consumer = new CollectConsumer<>();
    TagNameReferenceCompletionProvider.collectCompletionVariants((TagNameReference) ref, consumer);
    assertEquals(1, consumer.getResult().size());
    assertEquals("tag1", consumer.getResult().iterator().next().getLookupString());
  }

  public void testCompoundDtdDescriptor1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlElementDecl);
  }

  public void testCompoundDtdDescriptor2() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlElementDecl);
  }

  public void testDtdDescriptor2() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertEquals("simple.dtd", target.getContainingFile().getName());
  }

  public void testSimpleSchema() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlTag);
  }

  public void testSchemaWithIncludes() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlTag);
  }

  public void testSimpleSchemaFromResources() throws Exception{
    final String url = "http://test";
    ExternalResourceManagerExImpl.registerResourceTemporarily(
      url,
      PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/resolve/schema/simple.xsd",
      getTestRootDisposable());

    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof XmlTag);
  }

  private PsiReference configure() throws Exception {
    String filePath = getTestName(false) + ".xml";
    final String fullPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/resolve/" + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));
    int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    myFile = createFile(vFile.getName(), fileText);
    myFile.putUserData(XmlUtil.TEST_PATH, vFile.getParent().getPath());
    return myFile.findReferenceAt(offset);
  }
}
