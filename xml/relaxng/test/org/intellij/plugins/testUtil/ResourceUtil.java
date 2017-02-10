package org.intellij.plugins.testUtil;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import junit.framework.TestCase;
import org.intellij.plugins.relaxNG.HighlightingTestBase;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.regex.Pattern;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 27.03.2008
*/
public class ResourceUtil {
  public static <TC extends TestCase & IdeaCodeInsightTestCase> void copyFiles(TC test) throws IOException {
    try {
      final Method method = test.getClass().getMethod(test.getName());
      CopyFile annotation = method.getAnnotation(CopyFile.class);
      if (annotation == null) {
        if ((annotation = method.getDeclaringClass().getAnnotation(CopyFile.class)) == null) {
          return;
        }
      }

      final String[] patterns = annotation.value();
      for (String pattern : patterns) {
        final File root = new File(HighlightingTestBase.getTestDataBasePath() + test.getTestDataPath());
        final ArrayList<File> files = new ArrayList<>();
        FileUtil.collectMatchedFiles(root, Pattern.compile(FileUtil.convertAntToRegexp(pattern)), files);

        final File temp = new File(test.getFixture().getTempDirPath());
        final String target = annotation.target();
        if (target.length() > 0) {
          Assert.assertEquals(files.size(), 1);
          final File destFile = new File(temp, FileUtil.getRelativePath(root, new File(root, target)));
          FileUtil.copy(files.get(0), destFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
        } else {
          for (File file : files) {
            final File destFile = new File(temp, FileUtil.getRelativePath(root, file));
            FileUtil.copy(file, destFile);
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destFile);
          }
        }
      }
    } catch (NoSuchMethodException e) {
      Assert.fail(e.getMessage());
    }
  }
}