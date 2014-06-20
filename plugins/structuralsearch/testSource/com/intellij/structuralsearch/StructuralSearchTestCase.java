package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

abstract class StructuralSearchTestCase extends LightQuickFixTestCase {
  protected MatchOptions options;
  protected Matcher testMatcher;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    StructuralSearchUtil.ourUseUniversalMatchingAlgorithm = false;
    testMatcher = new Matcher(getProject());
    options = new MatchOptions();
    options.setLooseMatching(true);
    options.setRecursiveSearch(true);
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected void tearDown() throws Exception {
    testMatcher = null;
    options = null;
    super.tearDown();
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern, FileType fileType) {
    return findMatches(in,pattern,filePattern, fileType).size();
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          boolean filePattern,
                                          FileType patternFileType,
                                          Language patternLanugage,
                                          FileType sourceFileType,
                                          String sourceExtension,
                                          boolean physicalSourceFile) {
    return findMatches(in, pattern, filePattern, patternFileType, patternLanugage, sourceFileType, sourceExtension, physicalSourceFile,
                       true);
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          boolean filePattern,
                                          FileType patternFileType,
                                          Language patternLanugage,
                                          FileType sourceFileType,
                                          String sourceExtension,
                                          boolean physicalSourceFile,
                                          boolean transform) {
    options.clearVariableConstraints();
    options.setSearchPattern(pattern);
    if (transform) {
      MatcherImplUtil.transform(options);
    }
    pattern = options.getSearchPattern();
    options.setFileType(patternFileType);
    options.setDialect(patternLanugage);

    MatcherImpl.validate(getProject(), options);
    return testMatcher.testFindMatches(in, pattern, options, filePattern, sourceFileType, sourceExtension, physicalSourceFile);
  }

  protected List<MatchResult> findMatches(String in, String pattern, boolean filePattern, FileType patternFileType) {
    return findMatches(in, pattern, filePattern, patternFileType, null, patternFileType, null, false);
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern) {
    return findMatchesCount(in, pattern,filePattern, StdFileTypes.JAVA);
  }

  protected int findMatchesCount(String in, String pattern) {
    return findMatchesCount(in,pattern,false);
  }

  protected List<MatchResult> findMatches(String in, String pattern) {
    return findMatches(in,pattern,false, StdFileTypes.JAVA);
  }
  
  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + fileName), CharsetToolkit.UTF8, true);
  }
}
