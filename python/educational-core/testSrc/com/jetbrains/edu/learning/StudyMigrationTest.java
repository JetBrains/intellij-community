package com.jetbrains.edu.learning;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.JdomKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class StudyMigrationTest {
  @Test
  public void testFromThirdToForth() throws JDOMException, IOException, StudySerializationUtils.StudyUnrecognizedFormatException {
    Element element = JdomKt.loadElement(getTestDataPath().resolve("3.xml"));
    assertThat(StudySerializationUtils.Xml.convertToForthVersion(element)).isEqualTo(getTestDataPath().resolve("4.xml"));
  }

  @Test
  public void testAdaptive45() throws JDOMException, IOException, StudySerializationUtils.StudyUnrecognizedFormatException {
    Element element = JdomKt.loadElement(getTestDataPath().resolve("adaptive4.xml"));
    assertThat(StudySerializationUtils.Xml.convertToFifthVersion(element)).isEqualTo(getTestDataPath().resolve("adaptive5.xml"));
  }

  @Test
  public void testSubtasks45() throws JDOMException, IOException, StudySerializationUtils.StudyUnrecognizedFormatException {
    Element element = JdomKt.loadElement(getTestDataPath().resolve("subtasks4.xml"));
    assertThat(StudySerializationUtils.Xml.convertToFifthVersion(element)).isEqualTo(getTestDataPath().resolve("subtasks5.xml"));
  }

  @NotNull
  protected Path getTestDataPath() {
    return Paths.get(PlatformTestUtil.getCommunityPath(), "python/educational-core/testData/migration");
  }
}
