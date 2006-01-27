package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.testFramework.IdeaTestCase;

abstract class StructuralSearchTestCase extends IdeaTestCase {
  protected MatchOptions options;
  protected Matcher testMatcher;

  protected void setUp() throws Exception {
    super.setUp();

    testMatcher = new Matcher(myProject);
    options = new MatchOptions();
    options.setLooseMatching( true );
    options.setRecursiveSearch(true);
    PsiManager.getInstance(myProject).setEffectiveLanguageLevel(LanguageLevel.JDK_1_4);
  }

  @Override
  protected void tearDown() throws Exception {
    testMatcher = null;
    options = null;
    super.tearDown();
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern, FileType fileType) {
    options.clearVariableConstraints();
    options.setSearchPattern(pattern);
    MatcherImplUtil.transform(options);
    pattern = options.getSearchPattern();
    options.setFileType(fileType);

    MatcherImpl.validate(myProject, options);
    return testMatcher.testFindMatches(in,pattern,options,filePattern).size();
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern) {
    return findMatchesCount(in, pattern,filePattern, StdFileTypes.JAVA);
  }

  protected int findMatchesCount(String in, String pattern) {
    return findMatchesCount(in,pattern,false);
  }
}
