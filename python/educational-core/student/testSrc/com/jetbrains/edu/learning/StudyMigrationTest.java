package com.jetbrains.edu.learning;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class StudyMigrationTest {

  @Test
  public void testFromThirdToForth() throws JDOMException, IOException, StudySerializationUtils.StudyUnrecognizedFormatException {
    Element element = JDOMUtil.load(new File(FileUtil.join(getTestDataPath(), "3.xml")));
    Element actual = StudySerializationUtils.Xml.convertToForthVersion(element);
    Element expected = JDOMUtil.load(new File(FileUtil.join(getTestDataPath()), "4.xml"));
    PlatformTestUtil.assertElementsEqual(expected, actual);
  }

  protected String getTestDataPath() {
    return FileUtil.join(PlatformTestUtil.getCommunityPath(), "python/educational-core/student/testData/migration");
  }
}
