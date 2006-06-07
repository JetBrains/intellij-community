/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.mock;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;

public class MockProgressIndicator implements ProgressIndicator {
  private boolean myIsRunning = false;
  private boolean myIsCanceled = false;

  public void start() {
    myIsRunning = true;
  }

  public void stop() {
    myIsRunning = false;
  }

  public boolean isRunning() {
    return myIsRunning;
  }

  public void cancel() {
    myIsCanceled = true;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  public void setText(String text) {
  }

  public String getText() {
    return "";
  }

  public void setText2(String text) {
  }

  public String getText2() {
    return "";
  }

  public double getFraction() {
    return 1;
  }

  public void setFraction(double fraction) {
  }

  public void pushState() {
  }

  public void popState() {
  }

  public void startNonCancelableSection() {
  }

  public void finishNonCancelableSection() {
  }

  public boolean isModal() {
    return false;
  }

  public ModalityState getModalityState() {
    return ModalityState.NON_MMODAL;
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
  }

  public boolean isIndeterminate() {
    return false;
  }

  public void setIndeterminate(boolean indeterminate) {
  }

  public void checkCanceled() {
  }
}