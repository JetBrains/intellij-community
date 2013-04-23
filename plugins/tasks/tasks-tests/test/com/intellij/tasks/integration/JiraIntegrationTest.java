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
package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.tasks.jira.JiraRepositoryType;

/**
 * @author Dmitry Avdeev
 *         Date: 1/15/13
 */
public class JiraIntegrationTest extends TaskManagerTestCase {

  private JiraRepository myRepository;

  public void testGerman() throws Exception {
    myRepository.setUsername("german");
    myRepository.setPassword("german");
    Task[] issues = myRepository.getIssues(null, 50, 0);
    assertEquals(3, issues.length);
    assertEquals(TaskState.OPEN, issues[0].getState());
    assertFalse(issues[0].isClosed());
  }

  public void testLogin() throws Exception {
    myRepository.setUsername("german");
    myRepository.setUsername("wrong password");
    Exception exception = myRepository.createCancellableConnection().call();
    assertNotNull(exception);
    assertEquals(JiraRepository.LOGIN_FAILED_CHECK_YOUR_PERMISSIONS, exception.getMessage());
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new JiraRepository(new JiraRepositoryType());
    myRepository.setUrl("http://trackers-tests.labs.intellij.net:8015");
  }
}
