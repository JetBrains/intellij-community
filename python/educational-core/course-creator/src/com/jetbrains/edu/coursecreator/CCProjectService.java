/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.jetbrains.edu.learning.StudySerializationUtils.*;
import static com.jetbrains.edu.learning.StudySerializationUtils.Xml.*;

/**
 * @deprecated since version 3
 */
@State(name = "CCProjectService", storages = @Storage("course_service.xml"))
public class CCProjectService implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(CCProjectService.class);
  private Course myCourse;
  @Transient private final Project myProject;

  public CCProjectService() {
    this(null);
  }

  public CCProjectService(Project project) {
    myProject = project;
  }

  public Course getCourse() {
    return myCourse;
  }

  public void setCourse(Course course) {
    myCourse = course;
  }

  @Override
  public Element getState() {
    if (myCourse == null) {
      return null;
    }
    return XmlSerializer.serialize(this);
  }

  @Override
  public void loadState(Element state) {
    try {
      Element courseElement = getChildWithName(state, COURSE).getChild(COURSE_TITLED);
      for (Element lesson : getChildList(courseElement, LESSONS, true)) {
        int lessonIndex = getAsInt(lesson, INDEX);
        for (Element task : getChildList(lesson, TASK_LIST, true)) {
          int taskIndex = getAsInt(task, INDEX);
          Map<String, Element> taskFiles = getChildMap(task, TASK_FILES, true);
          for (Map.Entry<String, Element> entry : taskFiles.entrySet()) {
            Element taskFileElement = entry.getValue();
            String name = entry.getKey();
            String answerName = FileUtil.getNameWithoutExtension(name) + CCUtils.ANSWER_EXTENSION_DOTTED + FileUtilRt.getExtension(name);
            Document document = StudyUtils.getDocument(myProject.getBasePath(), lessonIndex, taskIndex, answerName);
            if (document == null) {
              document = StudyUtils.getDocument(myProject.getBasePath(), lessonIndex, taskIndex, name);
              if (document == null) {
                continue;
              }
            }
            for (Element placeholder : getChildList(taskFileElement, ANSWER_PLACEHOLDERS, true)) {
              Element lineElement = getChildWithName(placeholder, LINE, true);
              int line = lineElement != null ? Integer.valueOf(lineElement.getAttributeValue(VALUE)) : 0;
              Element startElement = getChildWithName(placeholder, START, true);
              int start = startElement != null ? Integer.valueOf(startElement.getAttributeValue(VALUE)) : 0;
              int offset = document.getLineStartOffset(line) + start;
              addChildWithName(placeholder, OFFSET, offset);
              addChildWithName(placeholder, "useLength", "false");
            }
          }
        }
      }
      XmlSerializer.deserializeInto(this, state);
    } catch (StudyUnrecognizedFormatException e) {
      LOG.error(e);
    }
  }

  public static CCProjectService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CCProjectService.class);
  }
}
