package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

/**
 * Heavyweight integration tests of skeletons of Python binary modules.
 * <p/>
 * An environment test environment must have a 'skeletons' tag in order to be compatible with this test case. No specific packages are
 * required currently. Both Python 2 and Python 3 are OK. All platforms are OK. At least one Python 2.6+ environment is required.
 *
 * @author vlan
 */
public class PythonSkeletonsTest extends PyEnvTestCase {
  public static final ImmutableSet<String> TAGS = ImmutableSet.of("skeletons");

  public void testBuiltins() {
    runTest(new SkeletonsTask() {
      @Override
      public void runTestOn(@NotNull Sdk sdk) {
        // Check the builtin skeleton header
        final Project project = myFixture.getProject();
        final PyFile builtins = PyBuiltinCache.getBuiltinsForSdk(project, sdk);
        assertNotNull(builtins);
        final VirtualFile virtualFile = builtins.getVirtualFile();
        assertNotNull(virtualFile);
        assertTrue(virtualFile.isInLocalFileSystem());
        final String path = virtualFile.getPath();
        final PySkeletonRefresher.SkeletonHeader header = PySkeletonRefresher.readSkeletonHeader(new File(path));
        assertNotNull(header);
        final int version = header.getVersion();
        assertTrue("Header version must be > 0, currently it is " + version, version > 0);
        assertEquals(SkeletonVersionChecker.BUILTIN_NAME, header.getBinaryFile());

        // Run inspections on a file that uses builtins
        edt(() -> myFixture.configureByFile(getTestName(false) + ".py"));

        PsiFile expr = myFixture.getFile();

        final Module module = ModuleUtilCore.findModuleForPsiElement(expr);

        final Sdk sdkFromModule = PythonSdkType.findPythonSdk(module);
        assertNotNull(sdkFromModule);

        final Sdk sdkFromPsi = PyBuiltinCache.findSdkForFile(expr.getContainingFile());
        assertNotNull(sdkFromPsi);
        final PyFile builtinsFromSdkCache = PythonSdkPathCache.getInstance(project, sdkFromPsi).getBuiltins().getBuiltinsFile();
        assertNotNull(builtinsFromSdkCache);
        assertEquals(builtins, builtinsFromSdkCache);

        final PyFile builtinsFromPsi = PyBuiltinCache.getInstance(expr).getBuiltinsFile();
        assertNotNull(builtinsFromPsi);
        assertEquals(builtins, builtinsFromPsi);

        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        edt(() -> myFixture.checkHighlighting(true, false, false));
      }
    });
  }

  // PY-4349
  public void testFakeNamedTuple() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        // Named tuples have been introduced in Python 2.6
        if (languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
          return;
        }

        // XXX: A workaround for invalidating VFS cache with the test file copied to our temp directory
        LocalFileSystem.getInstance().refresh(false);

        // Run inspections on code that uses named tuples
        edt(() -> myFixture.configureByFile(getTestName(false) + ".py"));
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);

        edt(() -> myFixture.checkHighlighting(true, false, false));
      }
    });
  }

  public void testKnownPropertiesTypes() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        myFixture.configureByText(PythonFileType.INSTANCE,
                                  "expr = slice(1, 2).start\n");
        ApplicationManager.getApplication().runReadAction(() -> {
          final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
          final PsiFile file = myFixture.getFile();
          final TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);
          final PyType type = context.getType(expr);
          final String actualType = PythonDocumentationProvider.getTypeName(type, context);
          assertEquals("int", actualType);
        });
      }
    });
  }

  // PY-9797
  public void testReadWriteDeletePropertyDefault() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        // We rely on int.real property that is not explicitly annotated in the skeletons generator
        if (languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
          return;
        }
        final Project project = myFixture.getProject();
        final PyFile builtins = PyBuiltinCache.getBuiltinsForSdk(project, sdk);
        assertNotNull(builtins);
        ApplicationManager.getApplication().runReadAction(() -> {
          final PyClass cls = builtins.findTopLevelClass("int");
          assertNotNull(cls);
          final Property prop = cls.findProperty("real", true, null);
          assertNotNull(prop);
          assertIsNotNull(prop.getGetter());
          assertIsNotNull(prop.getSetter());
          assertIsNotNull(prop.getDeleter());
        });
      }

      private void assertIsNotNull(Maybe<PyCallable> accessor) {
        if (accessor.isDefined()) {
          assertNotNull(accessor.valueOrNull());
        }
      }
    });
  }

  // PY-17282
  public void testBinaryStandardModule() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        edt(() -> myFixture.configureByFile(getTestName(false) + ".py"));
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);

        edt(() -> myFixture.checkHighlighting(true, false, false));
      }
    });
  }


  private void runTest(@NotNull PyTestTask task) {
    runPythonTest(task);
  }


  private abstract static class SkeletonsTask extends PyExecutionFixtureTestTask {
    @Override
    protected String getTestDataPath() {
      return PythonTestUtil.getTestDataPath() + "/skeletons/";
    }

    @Override
    public void runTestOn(String sdkHome) throws Exception {
      final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
      runTestOn(sdk);
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return TAGS;
    }

    protected abstract void runTestOn(@NotNull Sdk sdk);
  }
}
