/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.util.Consumer;
import com.intellij.util.ValueHolder;
import com.intellij.util.containers.Convertor;
import com.intellij.util.continuation.TaskDescriptor;

// cache. persistent. by request
public class FactsCalculator<In, Out> {
  private final Project myProject;
  private final String myTaskTitle;
  private final ValueHolder<Out, In> myCache;
  private final Convertor<In, Out> myLive;

  public FactsCalculator(Project project, String taskTitle, ValueHolder<Out, In> cache, Convertor<In, Out> live) {
    myProject = project;
    myTaskTitle = taskTitle;
    myCache = cache;
    myLive = live;
  }

  @CalledInAwt
  public void get(final In in, final Consumer<Out> resultConsumer) {
    createRunOrContinuation(in, resultConsumer).execute();
  }

  private RunOrContinuation<Out> createRunOrContinuation(final In in, final Consumer<Out> resultConsumer) {
    return new RunOrContinuation<Out>(myProject, myTaskTitle) {
      @Override
      protected Out calculate() {
        return myCache.getValue(in);
      }
      @Override
      protected Out calculateLong() {
        final Out result = myLive.convert(in);
        if (result != null) {
          myCache.setValue(result, in);
        }
        return result;
      }
      @Override
      protected void processResult(Out out) {
        resultConsumer.consume(out);
      }
    };
  }

  @CalledInAwt
  public TaskDescriptor getTask(final In in, final Consumer<Out> resultConsumer) {
    return createRunOrContinuation(in, resultConsumer).getTask();
  }
}
