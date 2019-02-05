// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.run;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class MavenExecutionStatusListener {

  private final ExternalSystemTaskId mySystemTaskId;
  private final ExternalSystemTaskNotificationListener myListener;
  private final Set<MavenSubTask> runningTasks = ContainerUtil.newConcurrentSet();

  public MavenExecutionStatusListener(ExternalSystemTaskId systemTaskId, ExternalSystemTaskNotificationListener listener) {
    mySystemTaskId = systemTaskId;
    myListener = listener;
  }

  public MavenSubTask start(String name) {
    return new MavenSubTask(name, null);
  }

  public void failAllNonCompleted(Exception e) {
    Set<MavenSubTask> toFail = ContainerUtil.newHashSet(runningTasks);

    for (MavenSubTask subTask : toFail) {
      subTask.failure(e);
    }
  }

  public class MavenSubTask {
    private final String myName;
    private final String myParentName;
    private final long myStartTime;
    private final TaskOperationDescriptor myDescriptor;

    MavenSubTask(String name, String parentName) {
      myName = name;
      myParentName = parentName;
      myStartTime = System.currentTimeMillis();

      myDescriptor = new TaskOperationDescriptorImpl(name, myStartTime, name);

      myListener.onStatusChange(new ExternalSystemTaskExecutionEvent(mySystemTaskId,
                                                                     new ExternalSystemStartEventImpl<>(
                                                                       myName, myParentName,
                                                                       myDescriptor
                                                                     ))
      );
      runningTasks.add(this);
    }

    public MavenSubTask startChild(String name) {
      return new MavenSubTask(name, myName);
    }

    public void failure(Throwable e) {
      myListener.onStatusChange(new ExternalSystemTaskExecutionEvent(mySystemTaskId,
                                                                     new ExternalSystemFinishEventImpl<>(
                                                                       myName, myParentName,
                                                                       myDescriptor,
                                                                       new FailureResultImpl(myStartTime, System.currentTimeMillis(),
                                                                                             getFailures(e)))
      ));
      runningTasks.remove(this);
    }

    public void failure(List<String> messages) {
      myListener.onStatusChange(new ExternalSystemTaskExecutionEvent(mySystemTaskId,
                                                                     new ExternalSystemFinishEventImpl<>(
                                                                       myName, myParentName,
                                                                       myDescriptor,
                                                                       new FailureResultImpl(myStartTime, System.currentTimeMillis(),
                                                                                             getFailures(messages)))
      ));
      runningTasks.remove(this);
    }


    public void success() {
      myListener.onStatusChange(new ExternalSystemTaskExecutionEvent(mySystemTaskId,
                                                                     new ExternalSystemFinishEventImpl<>(
                                                                       myName, myParentName,
                                                                       myDescriptor,
                                                                       new SuccessResultImpl(myStartTime, System.currentTimeMillis(),
                                                                                             false))
      ));
      runningTasks.remove(this);
    }

    public void skip() {
      myListener.onStatusChange(new ExternalSystemTaskExecutionEvent(mySystemTaskId,
                                                                     new ExternalSystemFinishEventImpl<>(
                                                                       myName, myParentName,
                                                                       myDescriptor,
                                                                       new SkippedResultImpl(myStartTime, System.currentTimeMillis()))
      ));
      runningTasks.remove(this);
    }


    private List<Failure> getFailures(Throwable e) {
      return Collections.singletonList(new FailureImpl(e.getMessage(), e.getMessage(), ContainerUtil.emptyList()));
    }

    private List<Failure> getFailures(List<String> messages) {
      List<Failure> result = new SmartList<>();
      for (String message : messages) {
        result.add(new FailureImpl(message, message, ContainerUtil.emptyList()));
      }
      ;
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MavenSubTask task = (MavenSubTask)o;
      return myName.equals(task.myName) &&
             Objects.equals(myParentName, task.myParentName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName, myParentName);
    }
  }
}
