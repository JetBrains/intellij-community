/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.testFramework.EdtTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker;
import com.jetbrains.python.toolbox.Maybe;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static com.jetbrains.python.fixtures.PyTestCase.assertType;
import static org.junit.Assert.*;

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

  @Test
  public void testBuiltins() {
    runTest(new SkeletonsTask() {
      @Override
      public void runTestOn(@NotNull Sdk sdk) {
        // Check the builtin skeleton header
        final Project project = myFixture.getProject();
        ApplicationManager.getApplication().runReadAction(() -> {
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
        });

        // Run inspections on a file that uses builtins
        EdtTestUtil.runInEdtAndWait((() -> myFixture.configureByFile(getTestName(false) + ".py")));

        ApplicationManager.getApplication().runReadAction(() -> {
          PsiFile expr = myFixture.getFile();

          final Module module = ModuleUtilCore.findModuleForPsiElement(expr);

          final Sdk sdkFromModule = PythonSdkType.findPythonSdk(module);
          assertNotNull(sdkFromModule);

          final Sdk sdkFromPsi = PyBuiltinCache.findSdkForFile(expr.getContainingFile());
          assertNotNull(sdkFromPsi);
          final PyFile builtinsFromSdkCache = PythonSdkPathCache.getInstance(project, sdkFromPsi).getBuiltins().getBuiltinsFile();
          assertNotNull(builtinsFromSdkCache);
          final PyFile builtins = PyBuiltinCache.getBuiltinsForSdk(project, sdk);
          assertNotNull(builtins);
          assertEquals(builtins, builtinsFromSdkCache);

          final PyFile builtinsFromPsi = PyBuiltinCache.getInstance(expr).getBuiltinsFile();
          assertNotNull(builtinsFromPsi);
          assertEquals(builtins, builtinsFromPsi);
        });

        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        EdtTestUtil.runInEdtAndWait((() -> myFixture.checkHighlighting(true, false, false)));
      }
    });
  }

  // PY-4349
  @Test
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
        EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByFile(getTestName(false) + ".py"));
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);

        EdtTestUtil.runInEdtAndWait(() -> myFixture.checkHighlighting(true, false, false));
      }
    });
  }

  @Test
  public void testKnownPropertiesTypes() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        myFixture.configureByText(PythonFileType.INSTANCE,
                                  "expr = slice(1, 2).start\n");
        ApplicationManager.getApplication().runReadAction(() -> {
          final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
          final PsiFile file = myFixture.getFile();
          assertType("int", expr, TypeEvalContext.codeAnalysis(file.getProject(), file));
        });
      }
    });
  }

  // PY-9797
  @Test
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
        ApplicationManager.getApplication().runReadAction(() -> {
          final PyFile builtins = PyBuiltinCache.getBuiltinsForSdk(project, sdk);
          assertNotNull(builtins);
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
  @Test
  public void testBinaryStandardModule() {
    runTest(new SkeletonsTask() {
      @Override
      protected void runTestOn(@NotNull Sdk sdk) {
        EdtTestUtil.runInEdtAndWait((() -> myFixture.configureByFile(getTestName(false) + ".py")));
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);

        EdtTestUtil.runInEdtAndWait((() -> myFixture.checkHighlighting(true, false, false)));
      }
    });
  }


  private void runTest(@NotNull PyTestTask task) {
    runPythonTest(task);
  }


  private abstract static class SkeletonsTask extends PyExecutionFixtureTestTask {
    SkeletonsTask() {
      super("/skeletons/");
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
