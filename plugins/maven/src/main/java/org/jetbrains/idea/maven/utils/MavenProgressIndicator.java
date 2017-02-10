/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;

import java.util.ArrayList;
import java.util.List;

public class MavenProgressIndicator {
  private ProgressIndicator myIndicator;
  private final List<Condition<MavenProgressIndicator>> myCancelConditions = new ArrayList<>();

  public MavenProgressIndicator() {
    this(new MyEmptyProgressIndicator());
  }

  public MavenProgressIndicator(ProgressIndicator i) {
    myIndicator = i;
  }

  public synchronized void setIndicator(ProgressIndicator i) {
    i.setText(myIndicator.getText());
    i.setText2(myIndicator.getText2());
    i.setFraction(myIndicator.getFraction());
    if (i.isCanceled()) i.cancel();
    myIndicator = i;
  }

  public synchronized ProgressIndicator getIndicator() {
    return myIndicator;
  }

  public synchronized void setText(String text) {
    myIndicator.setText(text);
  }

  public synchronized void setText2(String text) {
    myIndicator.setText2(text);
  }

  public synchronized void setFraction(double fraction) {
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

  public void checkCanceledNative() {
    if (isCanceled()) throw new ProcessCanceledException();
  }


  private static class MyEmptyProgressIndicator extends EmptyProgressIndicator {
    private String myText;
    private String myText2;
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
}
