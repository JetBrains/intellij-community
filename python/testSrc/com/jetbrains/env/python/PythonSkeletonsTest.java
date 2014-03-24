package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.env.python.debug.PyTestTask;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
        myFixture.configureByFile(getTestName(false) + ".py");


        PsiFile expr = myFixture.getFile();

        final Module module = ModuleUtil.findModuleForPsiElement(expr);

        final Sdk sdkFromModule = PythonSdkType.findPythonSdk(module);
        assertNotNull(sdkFromModule);

        final Sdk sdkFromPsi = PyBuiltinCache.findSdkForFile(expr.getContainingFile());
        final PyFile builtinsFromSdkCache = PythonSdkPathCache.getInstance(project, sdkFromPsi).getBuiltins().getBuiltinsFile();
        assertNotNull(builtinsFromSdkCache);
        assertEquals(builtins, builtinsFromSdkCache);

        final PyFile builtinsFromPsi = PyBuiltinCache.getInstance(expr).getBuiltinsFile();
        assertNotNull(builtinsFromPsi);
        assertEquals(builtins, builtinsFromPsi);

        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        edt(new Runnable() {
          @Override
          public void run() {
            myFixture.checkHighlighting(true, false, false);
          }
        });
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

        // Run inspections on code that uses named tuples
        myFixture.configureByFile(getTestName(false) + ".py");
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);

        edt(new Runnable() {
          @Override
          public void run() {
            myFixture.checkHighlighting(true, false, false);
          }
        });
      }
    });
  }

  public void testKnownPropertiesTypes() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        myFixture.configureByText(PythonFileType.INSTANCE,
                                  "expr = slice(1, 2).start\n");
        final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getFile());
        final PyType type = context.getType(expr);
        final String actualType = PythonDocumentationProvider.getTypeName(type, context);
        assertEquals("int", actualType);
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
        final PyClass cls = builtins.findTopLevelClass("int");
        assertNotNull(cls);
        final Property prop = cls.findProperty("real", true);
        assertNotNull(prop);
        assertIsNotNull(prop.getGetter());
        assertIsNotNull(prop.getSetter());
        assertIsNotNull(prop.getDeleter());
      }

      private void assertIsNotNull(Maybe<Callable> accessor) {
        if (accessor.isDefined()) {
          assertNotNull(accessor.valueOrNull());
        }
      }
    });
  }

  private void generateTempSkeletons(CodeInsightTestFixture fixture, final @NotNull Sdk sdk) throws InvalidSdkException, IOException {
    final Project project = fixture.getProject();
    ModuleRootModificationUtil.setModuleSdk(fixture.getModule(), sdk);

    edt(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ProjectRootManager.getInstance(project).setProjectSdk(sdk);
          }
        });
      }
    });


    final SdkModificator modificator = sdk.getSdkModificator();
    modificator.removeRoots(OrderRootType.CLASSES);
    for (String path : PythonSdkType.getSysPathsFromScript(sdk.getHomePath())) {
      PythonSdkType.addSdkRoot(modificator, path);
    }
    final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
    final File skeletonsDir = new File(tempDir, PythonSdkType.SKELETON_DIR_NAME);
    FileUtil.createDirectory(skeletonsDir);
    final String skeletonsPath = skeletonsDir.toString();
    PythonSdkType.addSdkRoot(modificator, skeletonsPath);

    edt(new Runnable() {
      @Override
      public void run() {
        modificator.commitChanges();
      }
    });

    final SkeletonVersionChecker checker = new SkeletonVersionChecker(0);
    final PySkeletonRefresher refresher = new PySkeletonRefresher(project, null, sdk, skeletonsPath, null, null);
    final List<String> errors = refresher.regenerateSkeletons(checker, null);
    assertEmpty(errors);
  }

  private void runTest(@NotNull PyTestTask task) {
    runPythonTest(task);
  }

  @NotNull
  public static Sdk createTempSdk(@NotNull String sdkHome) {
    final VirtualFile binary = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    assertNotNull("Interpreter file not found: " + sdkHome, binary);
    final Ref<Sdk> ref = Ref.create();
    edt(new Runnable() {

      @Override
      public void run() {
        final Sdk sdk = SdkConfigurationUtil.setupSdk(new Sdk[0], binary, PythonSdkType.getInstance(), true, null, null);
        assertNotNull(sdk);
        ref.set(sdk);
      }
    });

    return ref.get();
  }


  private abstract class SkeletonsTask extends PyExecutionFixtureTestTask {
    @Override
    protected String getTestDataPath() {
      return PythonTestUtil.getTestDataPath() + "/skeletons/";
    }

    @Override
    public void runTestOn(String sdkHome) throws Exception {
      final Sdk sdk = createTempSdk(sdkHome);
      generateTempSkeletons(myFixture, sdk);
      runTestOn(sdk);
    }

    @Override
    public Set<String> getTags() {
      return TAGS;
    }

    protected abstract void runTestOn(@NotNull Sdk sdk);
  }
}
