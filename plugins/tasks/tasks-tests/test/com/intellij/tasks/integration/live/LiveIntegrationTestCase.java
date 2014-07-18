package com.intellij.tasks.integration.live;

import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.TaskRepository;

/**
 * @author Mikhail Golubev
 */
public abstract class LiveIntegrationTestCase<T extends TaskRepository> extends TaskManagerTestCase {
  protected T myRepository;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepository = createRepository();
  }

  /**
   * @return new instance of task repository <b>with authenticated user<b/>
   */
  protected abstract T createRepository() throws Exception;
}
