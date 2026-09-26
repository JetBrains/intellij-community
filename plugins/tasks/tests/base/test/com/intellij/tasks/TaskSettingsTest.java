// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.generic.GenericRepositoryType;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.youtrack.YouTrackRepository;
import org.jdom.Element;

import java.util.Collections;

public class TaskSettingsTest extends TaskManagerTestCase {
  public void testCarriageReturnInFormat() throws Exception {
    TaskRepository repository = new YouTrackRepository();
    String format = "foo \n bar";
    repository.setCommitMessageFormat(format);
    myTaskManager.setRepositories(Collections.singletonList(repository));
    TaskManagerImpl.Config config = myTaskManager.getState();
    Element element = XmlSerializer.serialize(config);
    Element element1 = JDOMUtil.load(JDOMUtil.writeElement(element));
    TaskManagerImpl.Config deserialize = XmlSerializer.deserialize(element1, TaskManagerImpl.Config.class);
    myTaskManager.loadState(deserialize);

    TaskRepository[] repositories = myTaskManager.getAllRepositories();
    assertEquals(format, repositories[0].getCommitMessageFormat());
  }

  public void testEmptyState() {
    myTaskManager.loadState(new TaskManagerImpl.Config());
  }

  public void testGenericTestSerialization() {
    GenericRepositoryType type = new GenericRepositoryType();
    TaskRepository repository = type.new AsanaRepository().createRepository();
    myTaskManager.setRepositories(Collections.singletonList(repository));
    myTaskManager.getState();
  }
}
