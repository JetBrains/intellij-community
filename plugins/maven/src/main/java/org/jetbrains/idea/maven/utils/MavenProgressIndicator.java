// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.serviceContainer.AlreadyDisposedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;

import java.util.*;
import java.util.function.Supplier;

import static com.intellij.openapi.components.Service.Level.PROJECT;

public class MavenProgressIndicator {
  private ProgressIndicator myIndicator;
  private final List<Condition<MavenProgressIndicator>> myCancelConditions = new ArrayList<>();
  private @Nullable final Supplier<MavenSyncConsole> mySyncSupplier;
  private @Nullable final Project myProject;

  public MavenProgressIndicator(@Nullable Project project,
                                @Nullable Supplier<MavenSyncConsole> syncSupplier) {
    this(project, new MyEmptyProgressIndicator(), syncSupplier);
  }

  public MavenProgressIndicator(@Nullable Project project,
                                ProgressIndicator i,
                                @Nullable Supplier<MavenSyncConsole> syncSupplier) {
    myProject = project;
    myIndicator = i;
    mySyncSupplier = syncSupplier;
    maybeTrackIndicator(i);
  }

  public synchronized void setIndicator(ProgressIndicator i) {
    maybeTrackIndicator(i);
    //setIndicatorStatus(i);
    i.setText(myIndicator.getText());
    i.setText2(myIndicator.getText2());
    if (!i.isIndeterminate()) {
      i.setFraction(myIndicator.getFraction());
    }
    if (i.isCanceled()) i.cancel();
    myIndicator = i;
  }

  public synchronized ProgressIndicator getIndicator() {
    return myIndicator;
  }

  public synchronized void setText(@NlsContexts.ProgressText String text) {
    myIndicator.setText(text);
  }

  public synchronized void setText2(@NlsContexts.ProgressDetails String text) {
    myIndicator.setText2(text);
  }

  public synchronized void setFraction(double fraction) {
    myIndicator.setIndeterminate(false);
    myIndicator.setFraction(fraction);
  }

  public synchronized void setIndeterminate(boolean indeterminate) {
    myIndicator.setIndeterminate(indeterminate);
  }

  public synchronized void pushState() {
    myIndicator.pushState();
  }

  public synchronized void popState() {
    myIndicator.popState();
  }

  public synchronized void cancel() {
    myIndicator.cancel();
  }

  public synchronized void addCancelCondition(Condition<MavenProgressIndicator> condition) {
    myCancelConditions.add(condition);
  }

  public synchronized void removeCancelCondition(Condition<MavenProgressIndicator> condition) {
    myCancelConditions.remove(condition);
  }

  public synchronized boolean isCanceled() {
    if (myIndicator.isCanceled()) return true;
    for (Condition<MavenProgressIndicator> each : myCancelConditions) {
      if (each.value(this)) return true;
    }
    return false;
  }

  public void checkCanceled() throws MavenProcessCanceledException {
    if (isCanceled()) throw new MavenProcessCanceledException();
  }

  public void startedDownload(MavenServerProgressIndicator.ResolveType type, String id) {

    if (mySyncSupplier != null) {
      mySyncSupplier.get().getListener(type).downloadStarted(id);
    }
  }

  public void completedDownload(MavenServerProgressIndicator.ResolveType type, String id) {
    if (mySyncSupplier != null) {
      mySyncSupplier.get().getListener(type).downloadCompleted(id);
    }
  }

  public void failedDownload(MavenServerProgressIndicator.ResolveType type,
                             String id,
                             String message,
                             String trace) {
    if (mySyncSupplier != null) {
      mySyncSupplier.get().getListener(type).downloadFailed(id, message, trace);
    }
  }

  private static class MyEmptyProgressIndicator extends EmptyProgressIndicator {
    private @NlsContexts.ProgressText String myText;
    private @NlsContexts.ProgressDetails String myText2;
    private double myFraction;

    @Override
    public void setText(String text) {
      myText = text;
    }

    @Override
    public String getText() {
      return myText;
    }

    @Override
    public void setText2(String text) {
      myText2 = text;
    }

    @Override
    public String getText2() {
      return myText2;
    }

    @Override
    public void setFraction(double fraction) {
      myFraction = fraction;
    }

    @Override
    public double getFraction() {
      return myFraction;
    }
  }

  private void maybeTrackIndicator(@Nullable ProgressIndicator indicator) {
    if (myProject == null) return; // should we also wait for non-project process like MavenIndicesManager activities?
    if (indicator instanceof ProgressIndicatorEx) {
      myProject.getService(MavenProgressTracker.class).add(indicator);
      ((ProgressIndicatorEx)indicator).addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void start() {
          myProject.getService(MavenProgressTracker.class).add(indicator);
        }

        @Override
        public void stop() {
          myProject.getService(MavenProgressTracker.class).remove(indicator);
        }

        @Override
        public void cancel() {
          try {
            myProject.getService(MavenProgressTracker.class).remove(indicator);
          }
          catch (AlreadyDisposedException ignore) {
          }
        }
      });
    }
  }

  @ApiStatus.Internal
  @Service(PROJECT)
  public static final class MavenProgressTracker implements Disposable {
    private final Object myLock = new Object();
    private final Set<ProgressIndicator> myIndicators = Collections.newSetFromMap(new IdentityHashMap<>());

    public void waitForProgressCompletion() {
      while (hasMavenProgressRunning()) {
        final Object lock = new Object();
        synchronized (lock) {
          try {
            lock.wait(100);
          }
          catch (InterruptedException ignore) {
          }
        }
      }
    }

    @TestOnly
    public void assertProgressTasksCompleted() {
      synchronized (myLock) {
        if (!myIndicators.isEmpty()) {
          throw new AssertionError("Not finished tasks:\n" + StringUtil.join(myIndicators, ProgressIndicator::getText, "\n-----"));
        }
      }
    }

    private void add(@Nullable ProgressIndicator indicator) {
      synchronized (myLock) {
        myIndicators.add(indicator);
      }
    }

    private void remove(@Nullable ProgressIndicator indicator) {
      synchronized (myLock) {
        myIndicators.remove(indicator);
      }
    }

    private boolean hasMavenProgressRunning() {
      synchronized (myLock) {
        myIndicators.removeIf(indicator -> !indicator.isRunning());
        return !myIndicators.isEmpty();
      }
    }

    @Override
    public void dispose() {
      synchronized (myLock) {
        myIndicators.clear();
      }
    }
  }
}
