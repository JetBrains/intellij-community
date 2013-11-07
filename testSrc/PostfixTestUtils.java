import com.intellij.openapi.application.*;
import org.jetbrains.annotations.*;

import java.io.*;

public class PostfixTestUtils {
  @NotNull public static final String BASE_TEST_DATA_PATH = findTestDataPath();

  private static String findTestDataPath() {
    final File testData = new File("testData");
    if (testData.exists()) {
      return testData.getAbsolutePath();
    }

    return PathManager.getHomePath() + "/testData";
  }
}
