// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JUnit38AssumeSupportRunner.class)
public class XmlParsingAdditionalTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/psi/xml";
  }

  private static void transformAllChildren(final ASTNode file) {
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
  }

  public void testReparsePerformance() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new PsiAwareTextEditorProvider(),
                                                                            getTestRootDisposable());

    final Project project = getProject();

    final PsiFile file = myFixture.configureByFile("performance2.xml");
    assertNotNull(file);
    transformAllChildren(file.getNode());
    final Document doc = myFixture.getDocument(file);
    assertNotNull(doc);

    WriteCommandAction.writeCommandAction(project, file).run(
      () -> PlatformTestUtil.startPerformanceTest("XML reparse using PsiBuilder", 2500, () -> {
        for (int i = 0; i < 10; i++) {
          final long start = System.nanoTime();
          doc.insertString(0, "<additional root=\"tag\"/>");
          PsiDocumentManager.getInstance(project).commitDocument(doc);
          LOG.debug("Reparsed for: " + (System.nanoTime() - start));
        }
      }).assertTiming());
  }
}
