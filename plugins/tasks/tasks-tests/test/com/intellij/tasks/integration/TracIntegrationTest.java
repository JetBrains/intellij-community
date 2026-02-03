/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.tasks.TaskType;
import com.intellij.tasks.trac.TracRepository;

/**
 * @author Dmitry Avdeev
 */
public class TracIntegrationTest extends TaskManagerTestCase {

  private TracRepository myRepository;

  public void testTracEncoding() throws Exception {

    Task task = myRepository.findTask("5");
    assertNotNull(task);
    assertEquals("Artikel k\u00f6nnen nicht in den Warenkorb gelegt werden", task.getSummary());
  }

  public void testGetUnknownType() throws Exception {
    Task task = myRepository.findTask("5");
    assertNotNull(task);
    assertEquals(TaskType.BUG, task.getType());
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = new TracRepository();
    myRepository.setUrl("http://trackers-tests.labs.intellij.net:8012/trac-12-p-1/login/rpc");
    myRepository.setPassword("buildtest");
    myRepository.setUsername("buildtest");
    myRepository.setUseHttpAuthentication(true);
  }
}
