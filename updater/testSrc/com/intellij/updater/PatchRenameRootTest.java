/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

public class PatchRenameRootTest extends PatchTestCase {
  private PatchSpec myPatchSpec;
  private File myPatchApplyOldDir;
  private File myPatchApplyNewDir;
  private File myPatchFile;
  private File myBackupDir;

  private static Map<String, Long> digest(Patch patch, File dir) throws IOException, OperationCancelledException {
    return new TreeMap<>(patch.digestFiles(dir, Collections.emptyList(), false, TEST_UI));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myPatchFile = getTempFile("patch.zip");
    myPatchApplyOldDir = getTempFile(Paths.get("applyPatch", myOlderDir.getName()).toString());
    myPatchApplyNewDir = getTempFile(Paths.get("applyPatch", myNewerDir.getName()).toString());
    myPatchSpec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath())
      .setRenameRootDirectory(true);
    myBackupDir = getTempFile("backup");

    FileUtil.copyDir(myOlderDir, myPatchApplyOldDir);
  }

  @Test
  public void applyAndRevertBasic() throws Exception {
    TestScenario scenario = new TestScenario();
    scenario.run();
  }

  @Test
  public void applyAndRevertNewDirExistsAndEmpty() throws Exception {
    TestScenario scenario = new TestScenario() {
      @Override
      protected void beforePrepare() throws Exception {
        Files.createDirectories(myPatchApplyNewDir.toPath());
      }

      @Override
      protected void validateAfterApply(Map<String, Long> patchedOld, Map<String, Long> patchedNew) {
        assertEquals(myTarget, patchedOld);
        assertTrue(patchedNew.isEmpty());
      }
    };
    scenario.run();
  }

  @Test
  public void applyAndRevertNewDirNotEmptyAndNoAccess() throws Exception {
    TestScenario scenario = new TestScenario() {
      @Override
      protected void beforePrepare() throws Exception {
        FileUtil.copyDir(myOlderDir, myPatchApplyNewDir);
        Set<PosixFilePermission> perms000 = PosixFilePermissions.fromString("---------");
        try {
          Files.setPosixFilePermissions(myPatchApplyNewDir.toPath(), perms000);
        }
        catch (UnsupportedOperationException e) {
          assumeNoException(e);
        }
      }

      @Override
      protected void afterApply() throws Exception {
        // Make it possible to navigate to the directory to be able to compare contents
        Set<PosixFilePermission> perms755 = PosixFilePermissions.fromString("rwxr-xr-x");
        try {
          Files.setPosixFilePermissions(myPatchApplyNewDir.toPath(), perms755);
        }
        catch (UnsupportedOperationException e) {
          assumeNoException(e);
        }
      }

      @Override
      protected void validateAfterApply(Map<String, Long> patchedOld, Map<String, Long> patchedNew) {
        assertEquals(myTarget, patchedOld);
        assertEquals(myOriginal, patchedNew);
      }

      @Override
      protected void validateAfterRevert(Map<String, Long> revertedOld, Map<String, Long> revertedNew) {
        assertEquals(myOriginal, revertedOld);
        assertEquals(myOriginal, revertedNew);
      }
    };
    scenario.run();
  }

  @Test
  public void applyAndRevertNewDirNotEmpty() throws Exception {
    TestScenario scenario = new TestScenario() {
      @Override
      protected void beforePrepare() throws Exception {
        FileUtil.copyDir(myOlderDir, myPatchApplyNewDir);
      }

      @Override
      protected void validateAfterApply(Map<String, Long> patchedOld, Map<String, Long> patchedNew) {
        assertEquals(myTarget, patchedOld);
        assertEquals(myOriginal, patchedNew);
      }

      @Override
      protected void validateAfterRevert(Map<String, Long> revertedOld, Map<String, Long> revertedNew) {
        assertEquals(myOriginal, revertedOld);
        assertEquals(myOriginal, revertedNew);
      }
    };
    scenario.run();
  }

  private class TestScenario {
    protected Map<String, Long> myOriginal;
    protected Map<String, Long> myTarget;

    public void run() throws Exception {
      Map<String, ValidationResult.Option> options = new HashMap<>();

      Patch patch = PatchFileCreator.create(myPatchSpec, myPatchFile, TEST_UI);
      beforePrepare();

      PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myPatchFile, myPatchApplyOldDir, TEST_UI);
      validatePreparationResult(preparationResult.validationResults);

      myOriginal = digest(patch, myPatchApplyOldDir);
      myTarget = digest(patch, myNewerDir);

      List<PatchAction> appliedActions = PatchFileCreator.apply(preparationResult, options, myBackupDir, TEST_UI).appliedActions;
      afterApply();
      Map<String, Long> patchedOld = digest(patch, myPatchApplyOldDir);
      Map<String, Long> patchedNew = digest(patch, myPatchApplyNewDir);
      validateAfterApply(patchedOld, patchedNew);

      PatchFileCreator.revert(preparationResult, appliedActions, myBackupDir, TEST_UI);
      Map<String, Long> revertedNew = digest(patch, myPatchApplyNewDir);
      Map<String, Long> revertedOld = digest(patch, myPatchApplyOldDir);
      validateAfterRevert(revertedOld, revertedNew);
    }

    protected void beforePrepare() throws Exception {
    }

    protected void afterApply() throws Exception {
    }

    protected void validatePreparationResult(List<ValidationResult> validationResults) {
      assertTrue(validationResults.isEmpty());
    }

    protected void validateAfterApply(Map<String, Long> patchedOld, Map<String, Long> patchedNew) {
      assertEquals(myTarget, patchedNew);
      assertTrue(patchedOld.isEmpty());
    }

    protected void validateAfterRevert(Map<String, Long> revertedOld, Map<String, Long> revertedNew) {
      assertEquals(myOriginal, revertedOld);
      assertTrue(revertedNew.isEmpty());
    }
  }
}
