package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileLock;
import java.util.*;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PatchTest extends PatchTestCase {
  private Patch myPatch;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myPatch = new Patch(myOlderDir, myNewerDir, Collections.<String>emptyList(), Collections.<String>emptyList(),
                        Collections.<String>emptyList(), TEST_UI);
  }

  @Test
  public void testBasics() throws Exception {
    List<PatchAction> expectedActions = Arrays.asList(
      new CreateAction("newDir/newFile.txt"),
      new UpdateAction("Readme.txt", CHECKSUMS.README_TXT),
      new UpdateZipAction("lib/annotations.jar",
                          Arrays.asList("org/jetbrains/annotations/NewClass.class"),
                          Arrays.asList("org/jetbrains/annotations/Nullable.class"),
                          Arrays.asList("org/jetbrains/annotations/TestOnly.class"),
                          CHECKSUMS.ANNOTATIONS_JAR),
      new UpdateZipAction("lib/bootstrap.jar",
                          Collections.<String>emptyList(),
                          Collections.<String>emptyList(),
                          Arrays.asList("com/intellij/ide/ClassloaderUtil.class"),
                          CHECKSUMS.BOOTSTRAP_JAR),
      new DeleteAction("bin/idea.bat", CHECKSUMS.IDEA_BAT));
    List<PatchAction> actualActions = new ArrayList<PatchAction>(myPatch.getActions());
    Collections.sort(expectedActions, COMPARATOR);
    Collections.sort(actualActions, COMPARATOR);
    assertEquals(expectedActions, actualActions);
  }

  @Test
  public void testCreatingWithIgnoredFiles() throws Exception {
    myPatch = new Patch(myOlderDir,
                        myNewerDir,
                        Arrays.asList("Readme.txt", "bin/idea.bat"),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        TEST_UI);

    List<PatchAction> expectedActions = Arrays.asList(
      new CreateAction("newDir/newFile.txt"),
      new UpdateZipAction("lib/annotations.jar",
                          Arrays.asList("org/jetbrains/annotations/NewClass.class"),
                          Arrays.asList("org/jetbrains/annotations/Nullable.class"),
                          Arrays.asList("org/jetbrains/annotations/TestOnly.class"),
                          CHECKSUMS.ANNOTATIONS_JAR),
      new UpdateZipAction("lib/bootstrap.jar",
                          Collections.<String>emptyList(),
                          Collections.<String>emptyList(),
                          Arrays.asList("com/intellij/ide/ClassloaderUtil.class"),
                          CHECKSUMS.BOOTSTRAP_JAR));
    List<PatchAction> actualActions = new ArrayList<PatchAction>(myPatch.getActions());
    Collections.sort(expectedActions, COMPARATOR);
    Collections.sort(actualActions, COMPARATOR);
    assertEquals(expectedActions, actualActions);
  }

  @Test
  public void testValidation() throws Exception {
    FileUtil.writeToFile(new File(myOlderDir, "bin/idea.bat"), "changed".getBytes());
    new File(myOlderDir, "extraDir").mkdirs();
    new File(myOlderDir, "extraDir/extraFile.txt").createNewFile();
    new File(myOlderDir, "newDir").mkdirs();
    new File(myOlderDir, "newDir/newFile.txt").createNewFile();
    FileUtil.writeToFile(new File(myOlderDir, "Readme.txt"), "changed".getBytes());
    FileUtil.writeToFile(new File(myOlderDir, "lib/annotations.jar"), "changed".getBytes());
    FileUtil.delete(new File(myOlderDir, "lib/bootstrap.jar"));

    assertEquals(
      new HashSet<ValidationResult>(Arrays.asList(
        new ValidationResult(ValidationResult.Kind.CONFLICT,
                             "newDir/newFile.txt",
                             ValidationResult.Action.CREATE,
                             ValidationResult.ALREADY_EXISTS_MESSAGE,
                             ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP),
        new ValidationResult(ValidationResult.Kind.ERROR,
                             "Readme.txt",
                             ValidationResult.Action.UPDATE,
                             ValidationResult.MODIFIED_MESSAGE,
                             ValidationResult.Option.IGNORE),
        new ValidationResult(ValidationResult.Kind.ERROR,
                             "lib/annotations.jar",
                             ValidationResult.Action.UPDATE,
                             ValidationResult.MODIFIED_MESSAGE,
                             ValidationResult.Option.IGNORE),
        new ValidationResult(ValidationResult.Kind.ERROR,
                             "lib/bootstrap.jar",
                             ValidationResult.Action.UPDATE,
                             ValidationResult.ABSENT_MESSAGE,
                             ValidationResult.Option.IGNORE),
        new ValidationResult(ValidationResult.Kind.CONFLICT,
                             "bin/idea.bat",
                             ValidationResult.Action.DELETE,
                             ValidationResult.MODIFIED_MESSAGE,
                             ValidationResult.Option.DELETE, ValidationResult.Option.KEEP))),
      new HashSet<ValidationResult>(myPatch.validate(myOlderDir, TEST_UI)));
  }

  @Test
  public void testValidationWithOptionalFiles() throws Exception {
    FileUtil.writeToFile(new File(myOlderDir, "lib/annotations.jar"), "changed".getBytes());
    assertEquals(new HashSet<ValidationResult>(Arrays.asList(
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.IGNORE))),
                 new HashSet<ValidationResult>(myPatch.validate(myOlderDir, TEST_UI)));

    myPatch = new Patch(myOlderDir, myNewerDir, Collections.<String>emptyList(), Collections.<String>emptyList(),
                        Arrays.asList("lib/annotations.jar"), TEST_UI);
    FileUtil.delete(new File(myOlderDir, "lib/annotations.jar"));
    assertEquals(Collections.<ValidationResult>emptyList(),
                 myPatch.validate(myOlderDir, TEST_UI));
  }

  @Test
  public void testValidatingNonAccessibleFiles() throws Exception {
    File f = new File(myOlderDir, "Readme.txt");
    FileOutputStream s = new FileOutputStream(f, true);
    try {
      FileLock lock = s.getChannel().lock();
      try {
        List<ValidationResult> result = myPatch.validate(myOlderDir, TEST_UI);
        assertEquals(
          new HashSet<ValidationResult>(Arrays.asList(
            new ValidationResult(ValidationResult.Kind.ERROR,
                                 "Readme.txt",
                                 ValidationResult.Action.UPDATE,
                                 ValidationResult.ACCESS_DENIED_MESSAGE,
                                 ValidationResult.Option.IGNORE))),
          new HashSet<ValidationResult>(result));
      }
      finally {
        lock.release();
      }
    }
    finally {
      s.close();
    }
  }

  @Test
  public void testSaveLoad() throws Exception {
    File f = getTempFile("file");
    try {
      FileOutputStream out = new FileOutputStream(f);
      try {
        myPatch.write(out);
      }
      finally {
        out.close();
      }

      FileInputStream in = new FileInputStream(f);
      try {
        assertEquals(myPatch.getActions(), new Patch(in).getActions());
      }
      finally {
        in.close();
      }
    }
    finally {
      f.delete();
    }
  }

  private static final Comparator<PatchAction> COMPARATOR = new Comparator<PatchAction>() {
    @Override
    public int compare(PatchAction o1, PatchAction o2) {
      return o1.toString().compareTo(o2.toString());
    }
  };
}
