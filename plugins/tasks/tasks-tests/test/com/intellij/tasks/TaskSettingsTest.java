/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.youtrack.YouTrackRepository;
import org.jdom.Document;
import org.jdom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;

import static com.intellij.util.JdomKt.loadElement;

public class TaskSettingsTest extends TaskManagerTestCase {

  public void testCarriageReturnInFormat() throws Exception {
    TaskRepository repository = new YouTrackRepository();
    String format = "foo \n bar";
    repository.setCommitMessageFormat(format);
    myTaskManager.setRepositories(Collections.singletonList(repository));
    TaskManagerImpl.Config config = myTaskManager.getState();
    Element element = XmlSerializer.serialize(config);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    JDOMUtil.writeDocument(new Document(element), stream, "\n");

    Element element1 = loadElement(new ByteArrayInputStream(stream.toByteArray()));
    TaskManagerImpl.Config deserialize = XmlSerializer.deserialize(element1, TaskManagerImpl.Config.class);
    myTaskManager.loadState(deserialize);

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(format, repositories[0].getCommitMessageFormat());
  }
}
