package com.intellij.structuralsearch;

import com.intellij.analysis.AnalysisScope;
import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateManager;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.structuralsearch.duplicates.SSRDuplicatesProfile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class JSDuplicatesTest extends LightCodeInsightTestCase {
  @NonNls
  private static final String BASE_PATH = "/js/duplicates/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private static String readFile(File f) throws IOException {
    FileReader reader = new FileReader(f);
    StringBuilder builder = new StringBuilder();
    int n;
    do {
      n = reader.read();
      builder.append((char)n);
    }
    while (n != -1);
    builder.deleteCharAt(builder.length() - 1);
    return reduceWhitespaces(builder.toString());
  }

  private static String reduceWhitespaces(String s) {
    StringBuilder builder = new StringBuilder();
    boolean previousWhitespace = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        previousWhitespace = true;
      }
      else {
        if (previousWhitespace) {
          previousWhitespace = false;
          builder.append(' ');
        }
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private Set<String> readExpectedFiles(String testName, int count) throws IOException {
    Set<String> set = new HashSet<String>();
    String prefix = getTestFilesPrefix(testName);
    for (int i = 0; i < count; i++) {
      String expectedFilePath = prefix + i + ".dup";
      File expectedFile = new File(expectedFilePath);
      assert expectedFile.exists() : expectedFilePath + " not found";
      set.add(readFile(expectedFile));
    }
    return set;
  }

  private String getTestFilesPrefix(String testName) {
    return getTestDataPath() + BASE_PATH + testName + '_';
  }

  private static String merge(Collection<String> set) {
    StringBuilder builder = new StringBuilder();
    for (String s : set) {
      builder.append(s).append("\n\n");
    }
    return builder.toString();
  }

  private static String toString(DupInfo info, int index) {
    StringBuffer buffer = new StringBuffer();
    PsiFragment[] frags = info.getFragmentOccurences(index);
    for (int j = 0; j < frags.length; j++) {
      buffer.append("occurence ").append(j).append(":\n").append(frags[j]);
    }
    return buffer.toString();
  }

  private void doTest(String fileName, boolean distinguishVars, boolean distinguishMethods, boolean distinguishListerals, int patternCount)
    throws Exception {
    DuplocatorSettings settings = DuplocatorSettings.getInstance();
    boolean oldMethods = settings.DISTINGUISH_METHODS;
    boolean oldLits = settings.DISTINGUISH_LITERALS;
    boolean oldVars = settings.DISTINGUISH_VARIABLES;
    int oldLowerBound = settings.LOWER_BOUND;
    try{
      settings.DISTINGUISH_METHODS = distinguishMethods;
      settings.DISTINGUISH_LITERALS = distinguishListerals;
      settings.DISTINGUISH_VARIABLES = distinguishVars;
      settings.LOWER_BOUND = 2;

      DuplicatesProfile[] profiles = {new SSRDuplicatesProfile()};

      configureByFile(BASE_PATH + fileName);
      String testName = FileUtil.getNameWithoutExtension(fileName);

      int lowerBound = settings.LOWER_BOUND;
      DuplocatorHashCallback collector = new DuplocatorHashCallback(lowerBound);
      DuplocateManager.hash(new AnalysisScope(getFile()), collector, profiles, DuplocatorSettings.getInstance());

      DupInfo info = collector.getInfo();
      assertEquals(patternCount, info.getPatterns());
      Set<String> expectedFiles = readExpectedFiles(testName, patternCount);

      for (int i = 0; i < info.getPatterns(); i++) {
        String s = toString(info, i);
        s = reduceWhitespaces(s);
        assertTrue("Expected one of the \n" + merge(expectedFiles) + " but was\n" + s, expectedFiles.contains(s));
      }
    }
    finally {
      settings.DISTINGUISH_METHODS = oldMethods;
      settings.DISTINGUISH_LITERALS = oldLits;
      settings.DISTINGUISH_VARIABLES = oldVars;
      settings.LOWER_BOUND = oldLowerBound;
    }
  }

  public void test1() throws Exception {
    doTest("jsdup1.js", true, true, true, 2);
    doTest("jsdup1.js", true, false, true, 3);
    doTest("jsdup1.js", false, false, true, 4);
    doTest("jsdup1.js", false, false, false, 5);
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + "plugins/structuralsearch" + File.separatorChar + "testData";
  }
}

