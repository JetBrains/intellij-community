import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.util.*;
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

  @NotNull static String dumpItems(@Nullable LookupElement[] elements) {
    StringBuilder builder = new StringBuilder("// Items: ");

    if (elements != null && elements.length > 0) {
      boolean first = true;
      for (LookupElement item : elements) {
        if (first) first = false;
        else builder.append(", ");
        builder.append(item.getLookupString());
      }
    } else {
      builder.append("<no items>");
    }

    builder.append(SystemProperties.getLineSeparator());
    return builder.toString();
  }
}
