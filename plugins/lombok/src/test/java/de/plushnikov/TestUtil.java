package de.plushnikov;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestUtil {
  public static String getTestDataPath(String relativePath) {
    return getTestDataFile(relativePath).getPath() + File.separator;
  }

  private static File getTestDataFile(String relativePath) {
    return new File(getTestDataRoot(), relativePath);
  }

  private static File getTestDataRoot() {
    return new File("testData").getAbsoluteFile();
  }

  @NotNull
  public static String getTestDataPathRelativeToIdeaHome(@NotNull String relativePath) {
    File homePath = new File(PathManager.getHomePath());
    File testDir = new File(getTestDataRoot(), relativePath);

    String relativePathToIdeaHome = FileUtil.getRelativePath(homePath, testDir);
    if (relativePathToIdeaHome == null) {
      throw new RuntimeException("getTestDataPathRelativeToIdeaHome: FileUtil.getRelativePath('" + homePath +
        "', '" + testDir + "') returned null");
    }

    return relativePathToIdeaHome;
  }

  /*
   * For IntelliJ >= 2017.3 we need to call PsiTestUtil.addLibrary with Disposable parameter
   * For IntelliJ <2017.3 we can use default PsiTestUtil.addLibrary version
   */
  public static void addLibrary(@NotNull JavaCodeInsightTestFixture parent, Module module, String libName, String libPath, String jarArr) {
    Disposable projectDisposable;
    try {
//      projectDisposable = parent.getTestRootDisposable();
      final MethodHandle methodHandle = MethodHandles.lookup().findVirtual(JavaCodeInsightTestFixture.class, "getProjectDisposable", MethodType.methodType(Disposable.class));
      projectDisposable = (Disposable) methodHandle.invoke(parent);
    } catch (Throwable throwable) {
      projectDisposable = null;
    }

    if (null == projectDisposable) {
      PsiTestUtil.addLibrary(module, libName, libPath, jarArr);
    } else {
      addLibrary(projectDisposable, module, libName, libPath, jarArr);
    }
  }

  public static void addLibrary(@NotNull Disposable projectDisposable, Module module, String libName, String libPath, String jarArr) {
    final MethodHandle methodHandle;
    try {
//      PsiTestUtil.addLibrary(projectDisposable, module, libName, libPath, jarArr);
      methodHandle = MethodHandles.lookup().findStatic(PsiTestUtil.class, "addLibrary", MethodType.methodType(void.class,
        Disposable.class, Module.class, String.class, String.class, String[].class));
      methodHandle.invoke(projectDisposable, module, libName, libPath, new String[]{jarArr});
    } catch (Throwable throwable) {
      PsiTestUtil.addLibrary(module, libName, libPath, jarArr);
    }
  }

}
