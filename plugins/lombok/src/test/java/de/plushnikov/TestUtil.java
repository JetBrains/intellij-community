package de.plushnikov;

import java.io.File;

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

}
