package com.jetbrains.python;

import com.intellij.analysis.AnalysisScope;
import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateManager;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.duplocator.PyDuplicatesProfile;
import com.jetbrains.python.duplocator.PyDuplocatorSettings;
import com.jetbrains.python.fixtures.PyProfessionalTestCase;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User : ktisha
 */
public class PyDuplocatorTest extends PyProfessionalTestCase {
  private int myOldLowerBound;
  private PyDuplocatorSettings myDuplocatorSettings;

  public void testSimple() throws Exception {
    doTest(1);
  }

  public void testIgnoreNumericLiteral() throws Exception {
    myDuplocatorSettings.DISTINGUISH_LITERALS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_LITERALS = true;
    }
  }

  public void testIgnoreStringLiteral() throws Exception {
    myDuplocatorSettings.DISTINGUISH_LITERALS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_LITERALS = true;
    }
  }

  public void testIgnoreVariables() throws Exception {
    myDuplocatorSettings.DISTINGUISH_VARIABLES = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_VARIABLES = true;
    }
  }

  public void testIgnoreFields() throws Exception {
    myDuplocatorSettings.DISTINGUISH_FIELDS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_FIELDS = true;
    }
  }

  public void testFunctionCall() throws Exception {
    myDuplocatorSettings.DISTINGUISH_FUNCTIONS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_FUNCTIONS = true;
    }
  }

  public void testClassName() throws Exception {
    myDuplocatorSettings.DISTINGUISH_FIELDS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_FUNCTIONS = true;
    }
  }

  public void testStringMethodCall() throws Exception {
    myDuplocatorSettings.DISTINGUISH_METHODS = false;
    myDuplocatorSettings.DISTINGUISH_LITERALS = false;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_METHODS = true;
      myDuplocatorSettings.DISTINGUISH_LITERALS = true;
    }
  }

  public void testStringMethodCallNegative() throws Exception {
    myDuplocatorSettings.DISTINGUISH_METHODS = false;
    myDuplocatorSettings.LOWER_BOUND = 5;
    try {
      doTest(1);
    }
    finally {
      myDuplocatorSettings.LOWER_BOUND = 3;
      myDuplocatorSettings.DISTINGUISH_METHODS = true;
    }
  }

  public void testFunctionCallNegative() throws Exception {
    doTest(1);
  }

  public void testFunctionParametersNegative() throws Exception {
    myDuplocatorSettings.DISTINGUISH_VARIABLES = false;
    try {
      doTest(0);
    }
    finally {
      myDuplocatorSettings.DISTINGUISH_VARIABLES = true;
    }
  }

  protected void setUp() throws Exception {
    super.setUp();
    myDuplocatorSettings = PyDuplocatorSettings.getInstance();
    myOldLowerBound = myDuplocatorSettings.LOWER_BOUND;
    myDuplocatorSettings.LOWER_BOUND = 3;
  }

  protected void tearDown() throws Exception {
    myDuplocatorSettings.LOWER_BOUND = myOldLowerBound;
    super.tearDown();
  }

  private static String readFile(final File f) throws IOException {
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

  private static String reduceWhitespaces(final String s) {
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

  private Set<String> readExpectedFiles(final String testName, int count) throws IOException {
    final Set<String> set = new HashSet<String>();
    final String prefix = getTestFilesPrefix(testName);
    for (int i = 0; i < count; i++) {
      final String expectedFilePath = prefix + i + ".dup";
      final File expectedFile = new File(expectedFilePath);
      assert expectedFile.exists();
      set.add(readFile(expectedFile));
    }
    return set;
  }

  private String getTestFilesPrefix(final String testName) {
    return getTestDataPath() + '/' + testName + '_';
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/duplocator";
  }

  private static String merge(final Collection<String> set) {
    StringBuilder builder = new StringBuilder();
    for (String s : set) {
      builder.append(s).append("\n\n");
    }
    return builder.toString();
  }

  private static String toString(final DupInfo info, final int index) {
    final StringBuilder builder = new StringBuilder();
    PsiFragment[] frags = info.getFragmentOccurences(index);
    for (int j = 0; j < frags.length; j++) {
      builder.append("occurrence ").append(j).append(":\n").append(frags[j]);
    }
    return builder.toString();
  }

  public void doTest(final int patternCount) throws Exception {
    final String fileName = getTestName(true) + ".py";
    DuplicatesProfile[] profiles = {new PyDuplicatesProfile()};
    myFixture.configureByFile(fileName);
    final String testName = FileUtil.getNameWithoutExtension(getTestName(true));

    int lowerBound = myDuplocatorSettings.LOWER_BOUND;
    DuplocatorHashCallback collector = new DuplocatorHashCallback(lowerBound);
    // Due to API limitations we apply changes to RubyDuplocatorSettings,
    // but this method requires DuplocatorSettings instance
    DuplocateManager.hash(myFixture.getProject(), new AnalysisScope(myFixture.getFile()), collector, profiles, DuplocatorSettings.getInstance());

    final DupInfo info = collector.getInfo();
    assertEquals(patternCount, info.getPatterns());
    final Set<String> expectedFiles = readExpectedFiles(testName, patternCount);

    for (int i = 0; i < info.getPatterns(); i++) {
      String s = toString(info, i);
      s = reduceWhitespaces(s);
      assertTrue("Expected one of the \n" + merge(expectedFiles) + " but was\n" + s, expectedFiles.contains(s));
    }
  }
}