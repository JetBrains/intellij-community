package com.jetbrains.python.fixture;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public abstract class PythonCommonTestCase extends TestCase {
  protected PythonCommonCodeInsightTestFixture myFixture;

  protected abstract PythonCommonCodeInsightTestFixture getFixture();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = getFixture();
    myFixture.setUp();
  }

  @Override
  protected void runTest() throws Throwable {
    myFixture.runTest(() -> {
      super.runTest();
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      myFixture.addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  protected String getTestName(boolean lowercaseFirstLetter) {
    return getTestName(getName(), lowercaseFirstLetter);
  }

  protected void setLanguageLevel(@Nullable LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
  }

  protected void runWithLanguageLevel(@NotNull LanguageLevel languageLevel, @NotNull Runnable runnable) {
    setLanguageLevel(languageLevel);
    try {
      runnable.run();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  protected void runWithDocStringFormat(@NotNull DocStringFormat format, @NotNull Runnable runnable) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    final DocStringFormat oldFormat = settings.getFormat();
    settings.setFormat(format);
    try {
      runnable.run();
    }
    finally {
      settings.setFormat(oldFormat);
    }
  }

  protected void assertProjectFilesNotParsed(@NotNull PsiFile currentFile) {
    assertRootNotParsed(currentFile, myFixture.getTempDirRoot(), null);
  }

  protected void assertProjectFilesNotParsed(@NotNull TypeEvalContext context) {
    assertRootNotParsed(context.getOrigin(), myFixture.getTempDirRoot(), null);
  }

  protected void assertSdkRootsNotParsed(@NotNull PsiFile currentFile) {
    final Sdk testSdk = PythonSdkUtil.findPythonSdk(currentFile);
    for (VirtualFile root : testSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      assertRootNotParsed(currentFile, root, null);
    }
  }

  private void assertRootNotParsed(@NotNull PsiFile currentFile, @NotNull VirtualFile root, @Nullable TypeEvalContext context) {
    for (VirtualFile file : VfsUtil.collectChildrenRecursively(root)) {
      final PyFile pyFile = PyUtil.as(myFixture.getPsiManager().findFile(file), PyFile.class);
      if (pyFile != null && !pyFile.equals(currentFile) && (context == null || !context.maySwitchToAST(pyFile))) {
        assertNotParsed(pyFile);
      }
    }
  }

  @Contract("null, _ -> fail")
  @NotNull
  public static <T> T assertInstanceOf(Object o, @NotNull Class<T> aClass) {
    Assert.assertNotNull("Expected instance of: " + aClass.getName() + " actual: " + null, o);
    Assert.assertTrue("Expected instance of: " + aClass.getName() + " actual: " + o.getClass().getName(), aClass.isInstance(o));
    @SuppressWarnings("unchecked") T t = (T)o;
    return t;
  }

  @NotNull
  public static String getTestName(@NotNull String name, boolean lowercaseFirstLetter) {
    name = StringUtil.trimStart(name, "test");
    return StringUtil.isEmpty(name) ? "" : lowercaseFirstLetter(name, lowercaseFirstLetter);
  }

  @NotNull
  public static String lowercaseFirstLetter(@NotNull String name, boolean lowercaseFirstLetter) {
    if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  public static boolean isAllUppercaseName(@NotNull String name) {
    int uppercaseChars = 0;
    for (int i = 0; i < name.length(); i++) {
      if (Character.isLowerCase(name.charAt(i))) {
        return false;
      }
      if (Character.isUpperCase(name.charAt(i))) {
        uppercaseChars++;
      }
    }
    return uppercaseChars >= 3;
  }

  public static <T> void assertEmpty(@NotNull String errorMsg, @NotNull Collection<? extends T> collection) {
    assertOrderedEquals(errorMsg, collection, Collections.emptyList());
  }

  public static void assertEmpty(@NotNull Collection<?> collection) {
    assertEmpty(collection.toString(), collection);
  }

  public static void assertEmpty(Object @NotNull [] array) {
    assertOrderedEquals(array);
  }

  @NotNull
  public static String toString(@NotNull Iterable<?> collection) {
    if (!collection.iterator().hasNext()) {
      return "<empty>";
    }

    final StringBuilder builder = new StringBuilder();
    for (final Object o : collection) {
      if (o instanceof Set) {
        builder.append(new TreeSet<>((Set<?>)o));
      }
      else {
        builder.append(o);
      }
      builder.append('\n');
    }
    return builder.toString();
  }

  private static <T> boolean equals(@NotNull Iterable<? extends T> a1,
                                    @NotNull Iterable<? extends T> a2,
                                    @NotNull BiPredicate<? super T, ? super T> comparator) {
    Iterator<? extends T> it1 = a1.iterator();
    Iterator<? extends T> it2 = a2.iterator();
    while (it1.hasNext() || it2.hasNext()) {
      if (!it1.hasNext() || !it2.hasNext() || !comparator.test(it1.next(), it2.next())) {
        return false;
      }
    }
    return true;
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(T @NotNull [] actual, T @NotNull ... expected) {
    assertOrderedEquals(Arrays.asList(actual), expected);
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(@NotNull Iterable<? extends T> actual, T @NotNull ... expected) {
    assertOrderedEquals("", actual, expected);
  }

  public static void assertOrderedEquals(byte @NotNull [] actual, byte @NotNull [] expected) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < actual.length; i++) {
      byte a = actual[i];
      byte e = expected[i];
      assertEquals("not equals at index: " + i, e, a);
    }
  }

  public static void assertOrderedEquals(int @NotNull [] actual, int @NotNull [] expected) {
    if (actual.length != expected.length) {
      fail("Expected size: " +
           expected.length +
           "; actual: " +
           actual.length +
           "\nexpected: " +
           Arrays.toString(expected) +
           "\nactual  : " +
           Arrays.toString(actual));
    }
    for (int i = 0; i < actual.length; i++) {
      int a = actual[i];
      int e = expected[i];
      assertEquals("not equals at index: " + i, e, a);
    }
  }

  @SafeVarargs
  public static <T> void assertOrderedEquals(@NotNull String errorMsg, @NotNull Iterable<? extends T> actual, T @NotNull ... expected) {
    assertOrderedEquals(errorMsg, actual, Arrays.asList(expected));
  }

  public static <T> void assertOrderedEquals(@NotNull Iterable<? extends T> actual, @NotNull Iterable<? extends T> expected) {
    assertOrderedEquals("", actual, expected);
  }

  public static <T> void assertOrderedEquals(@NotNull String errorMsg,
                                             @NotNull Iterable<? extends T> actual,
                                             @NotNull Iterable<? extends T> expected) {
    assertOrderedEquals(errorMsg, actual, expected, (t, t2) -> Objects.equals(t, t2));
  }

  public static <T> void assertOrderedEquals(@NotNull String errorMsg,
                                             @NotNull Iterable<? extends T> actual,
                                             @NotNull Iterable<? extends T> expected,
                                             @NotNull BiPredicate<? super T, ? super T> comparator) {
    if (!equals(actual, expected, comparator)) {
      String expectedString = toString(expected);
      String actualString = toString(actual);
      Assert.assertEquals(errorMsg, expectedString, actualString);
      Assert.fail("Warning! 'toString' does not reflect the difference.\nExpected: " + expectedString + "\nActual: " + actualString);
    }
  }

  protected static void assertNotParsed(PsiFile file) {
    assertInstanceOf(file, PyFileImpl.class);
    assertNull("Operations should have been performed on stubs but caused file to be parsed: " + file.getVirtualFile().getPath(),
               ((PyFileImpl)file).getTreeElement());
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  @SafeVarargs
  public static <T> void assertSameElements(T @NotNull [] actual, T @NotNull ... expected) {
    assertSameElements(Arrays.asList(actual), expected);
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  @SafeVarargs
  public static <T> void assertSameElements(@NotNull Collection<? extends T> actual, T @NotNull ... expected) {
    assertSameElements(actual, Arrays.asList(expected));
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  public static <T> void assertSameElements(@NotNull Collection<? extends T> actual, @NotNull Collection<? extends T> expected) {
    assertSameElements("", actual, expected);
  }

  /**
   * Checks {@code actual} contains same elements (in {@link #equals(Object)} meaning) as {@code expected} irrespective of their order
   */
  public static <T> void assertSameElements(@NotNull String message, @NotNull Collection<? extends T> actual, @NotNull Collection<? extends T> expected) {
    if (actual.size() != expected.size() || !new HashSet<>(expected).equals(new HashSet<T>(actual))) {
      Assert.assertEquals(message, new HashSet<>(expected), new HashSet<T>(actual));
    }
  }

  @SafeVarargs
  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, T @NotNull ... expected) {
    assertContainsElements(collection, Arrays.asList(expected));
  }

  public static <T> void assertContainsElements(@NotNull Collection<? extends T> collection, @NotNull Collection<? extends T> expected) {
    ArrayList<T> copy = new ArrayList<>(collection);
    copy.retainAll(expected);
    assertSameElements(toString(collection), copy, expected);
  }

  @SafeVarargs
  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, T @NotNull ... notExpected) {
    assertDoesntContain(collection, Arrays.asList(notExpected));
  }

  public static <T> void assertDoesntContain(@NotNull Collection<? extends T> collection, @NotNull Collection<? extends T> notExpected) {
    ArrayList<T> expected = new ArrayList<>(collection);
    expected.removeAll(notExpected);
    assertSameElements(collection, expected);
  }

  public static void assertNullOrEmpty(@Nullable Collection<?> collection) {
    if (collection == null) return;
    assertEmpty("", collection);
  }

  public static void assertNotEmpty(@Nullable Collection<?> collection) {
    assertNotNull(collection);
    assertFalse(collection.isEmpty());
  }

  public static void assertSize(int expectedSize, Object @NotNull [] array) {
    if (array.length != expectedSize) {
      assertEquals(toString(Arrays.asList(array)), expectedSize, array.length);
    }
  }

  public static void assertSize(int expectedSize, @NotNull Collection<?> c) {
    if (c.size() != expectedSize) {
      assertEquals(toString(c), expectedSize, c.size());
    }
  }

  protected void runWithAdditionalClassEntryInSdkRoots(@NotNull VirtualFile directory, @NotNull Runnable runnable) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    assertNotNull(sdk);
    runWithAdditionalRoot(sdk, directory, OrderRootType.CLASSES, (__) -> runnable.run());
  }

  private static void runWithAdditionalRoot(@NotNull Sdk sdk,
                                            @NotNull VirtualFile root,
                                            @NotNull OrderRootType rootType,
                                            @NotNull Consumer<VirtualFile> rootConsumer) {
    WriteAction.run(() -> {
      final SdkModificator modificator = sdk.getSdkModificator();
      assertNotNull(modificator);
      modificator.addRoot(root, rootType);
      modificator.commitChanges();
    });
    try {
      rootConsumer.accept(root);
    }
    finally {
      WriteAction.run(() -> {
        final SdkModificator modificator = sdk.getSdkModificator();
        assertNotNull(modificator);
        modificator.removeRoot(root, rootType);
        modificator.commitChanges();
      });
    }
  }
}
