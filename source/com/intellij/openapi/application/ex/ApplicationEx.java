package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author max
 */
public interface ApplicationEx extends Application {
  /**
   * Loads the application configuration from the specified path
   *
   * @param optionsPath Path to /config folder
   * @throws IOException
   * @throws InvalidDataException
   */
  void load(String optionsPath) throws IOException, InvalidDataException;

  boolean isInternal();

  String getComponentsDescriptor();

  String getName();

  boolean holdsReadLock();

  void assertReadAccessToDocumentsAllowed();

  void doNotSave();

  boolean isDoNotSave();

  //force exit
  void exit(boolean force);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(final Runnable process,
                                              String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  /**
   * Whenever
   * - one thread acquired read action,
   * - launches another thread which is supposed to tun under read action too,
   * - and waits for that thread completion
   * it's a straight road to deadlock (because if anyone tries to start write action in the unlucky moment,
   * it will block waiting for the read action completion,
   * and the second readaction will block according to ReentrantWriterPreferenceReadWriteLock policy)
   *
   * So this is the only right way to wait multiple threads with read action for completion.
   */
  <T> List<Future<T>> invokeAllUnderReadAction(@NotNull Collection<Callable<T>> tasks, ExecutorService executorService) throws Throwable;
}
