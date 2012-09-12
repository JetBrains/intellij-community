/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Sep 8, 2006
 * Time: 3:40:30 PM
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @by Maxim.Mossienko
 */
public class XmlPerformanceTest extends LightQuickFixTestCase {
  private final Set<String> ourTestsWithFolding = new HashSet<String>(Arrays.asList("IndentUnindent2"));

  @Override
  protected String getBasePath() {
    return "performance/";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  public void testDummy() {
    @SuppressWarnings({"UnusedDeclaration"})
    Class clazz = IdeaTestUtil.class;
  }

  public void testIndentUnindent() throws Exception {
    doIndentTest(2000);
  }

  public void testIndentUnindent2() throws Exception {
    doIndentTest(2001);
  }

  @Override
  protected boolean doFolding() {
    return ourTestsWithFolding.contains(getTestName(false));
  }

  private void doIndentTest(int time) throws Exception {
    configureByFile(getBasePath() + getTestName(false)+".xml");
    doHighlighting();
    myEditor.getSelectionModel().setSelection(0,myEditor.getDocument().getTextLength());

    PlatformTestUtil.startPerformanceTest("Fix long indent/unindent "+time, time, new ThrowableRunnable() {
      @Override
      public void run() {
        EditorActionManager.getInstance().getActionHandler("EditorIndentSelection").execute(myEditor, new DataContext() {
          @Override
          @Nullable
          public Object getData(String dataId) {
            return null;
          }
        });

        EditorActionManager.getInstance().getActionHandler("EditorUnindentSelection").execute(myEditor, new DataContext() {
          @Override
          @Nullable
          public Object getData(String dataId) {
            return null;
          }
        });
      }
    }).assertTiming();
    final int startOffset = myEditor.getCaretModel().getOffset();
    myEditor.getSelectionModel().setSelection(startOffset,startOffset);
    checkResultByFile(getBasePath() + getTestName(false)+".xml");
  }
}
