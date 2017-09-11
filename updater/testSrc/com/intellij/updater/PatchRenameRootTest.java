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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.*;
import java.util.*;

import static java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT;
import static java.nio.file.attribute.AclEntryFlag.FILE_INHERIT;
import static java.nio.file.attribute.AclEntryPermission.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  /**
   * Revokes all permissions to a path. Returns a Closeable which restores permissions then closed.
   *
   * @param path File/directory to which revoke permissions.
   * @return {@link Closeable} object that restores permissions to the file when its {@code close} method get called.
   * @throws IOException if I/O error occurs
   */
  @NotNull
  private static Closeable revokePermissions(final Path path) throws IOException {
    if (SystemInfo.isWindows) {
      return revokePermissionsUsingAcls(path);
    }
    else {
      return revokePermissionsUsingPosix(path);
    }
  }

  /**
   * Posix implementation to revoke permissions to a file/directory. Equivalent of {@code chmod 000} on a file.
   */
  @NotNull
  private static Closeable revokePermissionsUsingPosix(Path path) throws IOException {
    final Set<PosixFilePermission> oldPermissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
    Set<PosixFilePermission> perms000 = PosixFilePermissions.fromString("---------");
    Files.setPosixFilePermissions(path, perms000);
    return () -> Files.setPosixFilePermissions(path, oldPermissions);
  }

  /**
   * Windows implementation to revoke permissions to a file/directory. Adds DENY ACL to file's ACL list.
   */
  @NotNull
  private static Closeable revokePermissionsUsingAcls(Path path) throws IOException {
    AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    UserPrincipal everyonePrincipal = null;
    try {
      everyonePrincipal = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
    }
    catch (UserPrincipalNotFoundException e) {
      Assume.assumeNoException("Special group 'Everyone' is missing. Non-english Windows locale?", e);
    }
    List<AclEntry> oldAclEntries = aclView.getAcl();
    List<AclEntry> newAclEntries = new ArrayList<>(oldAclEntries.size() + 1);

    AclEntry denyReadWriteToEveryoneAclEntry = AclEntry.newBuilder()
      .setPrincipal(everyonePrincipal)
      .setType(AclEntryType.DENY)
      .setPermissions(LIST_DIRECTORY, ADD_FILE, ADD_SUBDIRECTORY, DELETE, EXECUTE, DELETE_CHILD, WRITE_OWNER, SYNCHRONIZE)
      .setFlags(FILE_INHERIT, DIRECTORY_INHERIT)
      .build();

    // Order of ACLs matters. Deny should be first.
    newAclEntries.add(denyReadWriteToEveryoneAclEntry);
    newAclEntries.addAll(oldAclEntries);

    aclView.setAcl(newAclEntries);
    return () -> aclView.setAcl(oldAclEntries);
  }

  @Test
  public void applyAndRevertNewDirNotEmptyAndNoAccess() throws Exception {
    TestScenario scenario = new TestScenario() {
      private Closeable myRestorePermissionsToken;

      @Override
      protected void beforePrepare() throws Exception {
        FileUtil.copyDir(myOlderDir, myPatchApplyNewDir);
        myRestorePermissionsToken = PatchRenameRootTest.revokePermissions(myPatchApplyNewDir.toPath());
      }

      @Override
      protected void afterApply() throws Exception {
        // Restore permissions to the directory to validate its contents
        myRestorePermissionsToken.close();
        myRestorePermissionsToken = null;
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

      @Override
      protected void cleanup() throws IOException {
        if (myRestorePermissionsToken != null) {
          myRestorePermissionsToken.close();
          myRestorePermissionsToken = null;
        }
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
      try {
        Map<String, ValidationResult.Option> options = new HashMap<>();

        Patch patch = PatchFileCreator.create(myPatchSpec, myPatchFile, TEST_UI);
        beforePrepare();

        PatchFileCreator.PreparationResult preparationResult =
          PatchFileCreator.prepareAndValidate(myPatchFile, myPatchApplyOldDir, TEST_UI);
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
      finally {
        cleanup();
      }
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

    protected void cleanup() throws Exception {
    }
  }
}
