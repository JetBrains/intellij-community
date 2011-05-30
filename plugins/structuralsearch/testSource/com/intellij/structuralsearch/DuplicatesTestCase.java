package com.intellij.structuralsearch;

import com.intellij.analysis.AnalysisScope;
import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateManager;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.Language;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class DuplicatesTestCase extends LightCodeInsightTestCase {
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
    return getTestDataPath() + getBasePath() + testName + '_';
  }
  
  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + "plugins/structuralsearch" + File.separatorChar + "testData";
  }
  
  protected abstract String getBasePath();

  protected abstract Language[] getLanguages();

  protected void doTest(String fileName,
                        boolean distinguishVars,
                        boolean distinguishMethods,
                        boolean distinguishListerals,
                        int patternCount,
                        int patternCountWithDefaultEquivalence,
                        String suffix,
                        int lowerBound) throws Exception {
    if (patternCountWithDefaultEquivalence >= 0) {
      try {
        EquivalenceDescriptorProvider.ourUseDefaultEquivalence = true;
        findAndCheck(fileName, distinguishVars, distinguishMethods, distinguishListerals, patternCountWithDefaultEquivalence,
                     suffix + "_defeq", lowerBound);
      }
      finally {
        EquivalenceDescriptorProvider.ourUseDefaultEquivalence = false;
      }
    }
    findAndCheck(fileName, distinguishVars, distinguishMethods, distinguishListerals, patternCount, suffix, lowerBound);
  }

  private void findAndCheck(String fileName,
                            boolean distinguishVars,
                            boolean distinguishMethods,
                            boolean distinguishListerals,
                            int patternCount, String suffix, int lowerBound) throws Exception {
    DuplocatorSettings settings = DuplocatorSettings.getInstance();
    boolean oldMethods = settings.DISTINGUISH_METHODS;
    boolean oldLits = settings.DISTINGUISH_LITERALS;
    boolean oldVars = settings.DISTINGUISH_VARIABLES;
    int oldLowerBound = settings.LOWER_BOUND;
    Set<String> oldSet = settings.SELECTED_PROFILES;
    try{
      settings.DISTINGUISH_METHODS = distinguishMethods;
      settings.DISTINGUISH_LITERALS = distinguishListerals;
      settings.DISTINGUISH_VARIABLES = distinguishVars;
      settings.LOWER_BOUND = lowerBound;

      final List<DuplicatesProfile> profiles = new ArrayList<DuplicatesProfile>();

      settings.SELECTED_PROFILES = new com.intellij.util.containers.HashSet<String>();
      for (Language language : getLanguages()) {
        final DuplicatesProfile profile = DuplicatesProfile.findProfileForLanguage(DuplicatesProfile.getAllProfiles(), language);
        assert profile != null;
        profiles.add(profile);
        settings.SELECTED_PROFILES.add(language.getDisplayName());
      }

      configureByFile(getBasePath() + fileName);
      String testName = FileUtil.getNameWithoutExtension(fileName);

      DuplocatorHashCallback collector = new DuplocatorHashCallback(settings.LOWER_BOUND);
      DuplocateManager.hash(new AnalysisScope(getFile()), collector, profiles.toArray(new DuplicatesProfile[profiles.size()]),
                            DuplocatorSettings.getInstance());

      DupInfo info = collector.getInfo();
      assertEquals(patternCount, info.getPatterns());
      Set<String> expectedFiles = readExpectedFiles(testName + suffix, patternCount);

      for (int i = 0; i < info.getPatterns(); i++) {
        String s = toString(info, i);
        s = reduceWhitespaces(s);
        assertTrue("Expected one of the \n" + merge(expectedFiles) + " but was\n" + s, expectedFiles.contains(s));
      }
    }
    finally {
      settings.SELECTED_PROFILES = oldSet;
      settings.DISTINGUISH_METHODS = oldMethods;
      settings.DISTINGUISH_LITERALS = oldLits;
      settings.DISTINGUISH_VARIABLES = oldVars;
      settings.LOWER_BOUND = oldLowerBound;
    }
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
    String[] strFrags = new String[frags.length];

    for (int i = 0; i < frags.length; i++) {
      strFrags[i] = frags[i].toString();
    }
    Arrays.sort(strFrags);

    for (int i = 0; i < strFrags.length; i++) {
      buffer.append("occurence ").append(i).append(":\n").append(strFrags[i]);
    }
    return buffer.toString();
  }
}
