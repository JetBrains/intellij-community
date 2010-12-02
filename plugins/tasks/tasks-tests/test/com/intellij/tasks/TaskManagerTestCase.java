package com.intellij.tasks;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {

  protected TaskManager myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = TaskManager.getManager(getProject());
  }
}
