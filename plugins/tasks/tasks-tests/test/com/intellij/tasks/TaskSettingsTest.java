// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    Element element1 = JDOMUtil.load(new ByteArrayInputStream(stream.toByteArray()));
    TaskManagerImpl.Config deserialize = XmlSerializer.deserialize(element1, TaskManagerImpl.Config.class);
    myTaskManager.loadState(deserialize);

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(format, repositories[0].getCommitMessageFormat());
  }

  public void testEmptyState() {
    myTaskManager.loadState(new TaskManagerImpl.Config());
  }
}
