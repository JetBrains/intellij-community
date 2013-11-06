import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
