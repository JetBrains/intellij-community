// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.JavaResolveTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;

@HeavyPlatformTestCase.WrapInCommand
public class XmlResolve2Test extends JavaResolveTestCase {
  private static final String BASE_PATH = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/resolve/namespace/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  protected void configByFile() throws Exception {
    final String fullPath = BASE_PATH + getTestName(false) + ".xml";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));

    myFile = createFile(vFile.getName(), fileText);
  }

  private void doTest() throws Exception {
    configByFile();
    class Visitor extends XmlRecursiveElementVisitor {
      final StringBuffer myOutput = new StringBuffer();

      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        myOutput
          .append("  Attribute: ").append(attribute.getName())
          .append("(")
          .append(attribute.getNamespace().isEmpty() ? "default" : attribute.getNamespace())
          .append(")\n");
        visitXmlElement(attribute);
      }

      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        myOutput.append("Tag: ").append(tag.getName())
          .append("(").append(tag.getNamespace().isEmpty() ? "default" : tag.getNamespace())
          .append(")\n");
        visitXmlElement(tag);
      }

      public String getOutputString() {
        return myOutput.toString();
      }
    }
    Visitor visitor = new Visitor();
    myFile.accept(visitor);
    checkResult(getTestName(false) + ".txt", visitor.getOutputString());
  }

  protected void checkResult(String name, final String text) throws Exception {
    try {
      String expectedText = loadFile(name);
      assertEquals(expectedText, text);
    }
    catch (FileNotFoundException e) {
      String fullName = BASE_PATH + name;
      try (FileWriter writer = new FileWriter(fullName)) {
        writer.write(text);
      }
      fail("No output text found. Created.");
    }
  }

  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }
}
