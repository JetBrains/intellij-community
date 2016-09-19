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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.ThreadSafeTransparentlyFailedValue;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ValueHolder;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.CalledInAwt;

// cache. persistent. by request
public class FactsCalculator<In, Out, E extends Exception> {

  private static final Logger LOG = Logger.getInstance(FactsCalculator.class);

  private final String myTaskTitle;
  private final ValueHolder<Out, In> myCache;
  private final ThrowableConvertor<In, Out, E> myLive;

  public FactsCalculator(String taskTitle, ValueHolder<Out, In> cache, ThrowableConvertor<In, Out, E> live) {
    myTaskTitle = taskTitle;
    myCache = cache;
    myLive = live;
  }

  @CalledInAwt
  public TaskDescriptor getTask(final In in, final Consumer<TransparentlyFailedValueI<Out, E>> resultConsumer, final Class<E> clazzE) {
    TransparentlyFailedValueI<Out, E> value = new ThreadSafeTransparentlyFailedValue<>();
    final TaskDescriptor pooled = new TaskDescriptor(myTaskTitle, Where.POOLED) {
      @Override
      public void run(ContinuationContext context) {
        try {
          final Out calculatedValue = myLive.convert(in);
          if (calculatedValue != null) {
            myCache.setValue(calculatedValue, in);
          }
          value.set(calculatedValue);
        }
        catch (Exception e) {
          setException(value, e, clazzE);
        }
        context.next(new TaskDescriptor("final part", Where.AWT) {
          @Override
          public void run(ContinuationContext context) {
            resultConsumer.consume(value);
          }
        });
      }
    };

    return new TaskDescriptor("short part", Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        try {
          value.set(myCache.getValue(in));
        }
        catch (Exception e) {
          setException(value, e, clazzE);
        }
        if (value.haveSomething()) {
          resultConsumer.consume(value);
          return;
        }
        context.next(pooled);
      }
    };
  }

  private void setException(TransparentlyFailedValueI<Out, E> value, Exception e, Class<E> clazzE) {
    if (clazzE.isAssignableFrom(e.getClass())) {
      value.fail((E)e);
    }
    else {
      LOG.info(e);
      value.failRuntime(e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e));
    }
  }
}
