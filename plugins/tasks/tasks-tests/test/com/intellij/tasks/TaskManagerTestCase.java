package com.intellij.tasks;

import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {

  protected TaskManagerTestCase() {
    super(UsefulTestCase.IDEA_MARKER_CLASS, "PlatformLangXml");
  }

  protected TaskManagerImpl myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = (TaskManagerImpl)TaskManager.getManager(getProject());
  }
}
