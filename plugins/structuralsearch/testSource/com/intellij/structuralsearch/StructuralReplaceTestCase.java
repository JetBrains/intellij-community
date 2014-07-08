package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 11, 2005
 * Time: 10:10:48 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class StructuralReplaceTestCase extends LightQuickFixTestCase {
  protected Replacer replacer;
  protected ReplaceOptions options;
  protected String actualResult;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    StructuralSearchUtil.ourUseUniversalMatchingAlgorithm = false;

    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_4);

    options = new ReplaceOptions();
    options.setMatchOptions(new MatchOptions());
    replacer = new Replacer(getProject(), null);
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + FileUtilRt.getExtension(fileName) + "/" + fileName), CharsetToolkit.UTF8, true);
  }
}
