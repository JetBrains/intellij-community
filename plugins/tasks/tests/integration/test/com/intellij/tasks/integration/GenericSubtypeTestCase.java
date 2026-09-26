package com.intellij.tasks.integration;

import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class GenericSubtypeTestCase extends TaskManagerTestCase {
  protected GenericRepository myRepository;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final GenericRepositoryType genericType = new GenericRepositoryType();
    myRepository = createRepository(genericType);
  }

  @NotNull
  protected abstract GenericRepository createRepository(GenericRepositoryType genericType);
}
