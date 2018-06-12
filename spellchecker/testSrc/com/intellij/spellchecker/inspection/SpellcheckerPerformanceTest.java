/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.inspection;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.inspections.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class SpellcheckerPerformanceTest extends SpellcheckerInspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    long start = System.currentTimeMillis();
    super.setUp();
    System.out.println("setUp took " + (System.currentTimeMillis() - start) + " ms");
  }

  public void testLargeTextFileWithManyTypos() {
    final int typoCount = 5000;
    @SuppressWarnings("SpellCheckingInspection") String text = StringUtil.repeat("aaaaaaaaa ", typoCount);  // about 0.5M

    long start = System.currentTimeMillis();
    VirtualFile file = myFixture.addFileToProject("foo.txt", text).getVirtualFile();
    System.out.println("creation took " + (System.currentTimeMillis() - start) + " ms");

    start = System.currentTimeMillis();
    myFixture.configureFromExistingVirtualFile(file);
    System.out.println("configure took " + (System.currentTimeMillis() - start) + " ms");

    myFixture.enableInspections(getInspectionTools());

    start = System.currentTimeMillis();
    assertSize(typoCount, runLocalInspections());
    System.out.println("warm-up took " + (System.currentTimeMillis() - start) + " ms");

    PlatformTestUtil.startPerformanceTest("many typos highlighting", 12_000, () -> {
      DaemonCodeAnalyzer.getInstance(getProject()).restart();
      assertSize(typoCount, runLocalInspections());
    }).assertTiming();
  }

  public void testManyWhitespaces() {
    final int count = 100000;
    String text = StringUtil.repeat("//\n     \t      \t    \n   \n", count);

    long start = System.currentTimeMillis();
    VirtualFile file = myFixture.addFileToProject("foo.java", text).getVirtualFile();
    System.out.println("creation took " + (System.currentTimeMillis() - start) + " ms");

    start = System.currentTimeMillis();
    myFixture.configureFromExistingVirtualFile(file);
    System.out.println("configure took " + (System.currentTimeMillis() - start) + " ms");

    start = System.currentTimeMillis();
    List<HighlightInfo> infos = runLocalInspections();
    assertEmpty(infos);
    System.out.println("warm-up took " + (System.currentTimeMillis() - start) + " ms");

    PlatformTestUtil.startPerformanceTest("many whitespaces highlighting", 4000, () -> {
      DaemonCodeAnalyzer.getInstance(getProject()).restart();
      assertEmpty(runLocalInspections());
    }).assertTiming();
  }

  public void testVeryLongEmail(){
    final String text = "\\LONG_EMAIL: " + StringUtil.repeat("ivan.ivanov", 1000000) + "@mail.com\n";
    doSplitterPerformanceTest(text, CommentSplitter.getInstance(), 8000);
  }

  public void testVeryLongURL(){
    final String text = "\\LONG_URL:  http://" + StringUtil.repeat("ivan.ivanov", 1000000) + ".com\n";
    doSplitterPerformanceTest(text, CommentSplitter.getInstance(), 8000);
  }

  public void testVeryLongHTML(){
    final String text = "\\ LONG_HTML <!--<li>something go here</li>" + StringUtil.repeat("<li>next content</li>", 1000000) + "foooo barrrr <p> text -->";
    doSplitterPerformanceTest(text, CommentSplitter.getInstance(), 4000);
  }

  public void testVeryLongIdentifier(){
    final String text = StringUtil.repeat("identifier1", 1000000);
    doSplitterPerformanceTest(text, IdentifierSplitter.getInstance(), 3000);
  }

  public void testVeryLongSpecialCharacters(){
    final String text = "word" + StringUtil.repeat("\n\t\r\t\n", 1000000);
    doSplitterPerformanceTest(text, TextSplitter.getInstance(), 2000);
  }

  public void testVeryLongProperty(){
    final String text = StringUtil.repeat("properties.test.properties", 1000000);
    doSplitterPerformanceTest(text, PropertiesSplitter.getInstance(), 4000);
  }

  public void testVeryLongList(){
    final String text = StringUtil.repeat("properties,test,properties", 1000000);
    doSplitterPerformanceTest(text, PlainTextSplitter.getInstance(), 2000);
  }

  private static void doSplitterPerformanceTest(String text, Splitter splitter, int expectedTime) {
    PlatformTestUtil.startPerformanceTest("long word for spelling", expectedTime, () -> {
      try{
        splitter.split(text, TextRange.allOf(text), (textRange) -> {});
      }catch(ProcessCanceledException pce){
        System.out.println("pce is thrown");
      }
    }).attempts(1).assertTiming();
  }

  @NotNull
  private List<HighlightInfo> runLocalInspections() {
    myFixture.enableInspections(getInspectionTools());
    TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(getProject());
    List<TextEditorHighlightingPass> passes = passRegistrar.instantiatePasses(myFixture.getFile(), myFixture.getEditor(), new int[0]);
    int[] toIgnore = passes.stream().mapToInt(TextEditorHighlightingPass::getId).toArray();
    int i = ArrayUtil.find(toIgnore, Pass.LOCAL_INSPECTIONS);
    toIgnore[i] = 0; // ignore everything except Pass.LOCAL_INSPECTIONS

    return CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), toIgnore, false);
  }
}
