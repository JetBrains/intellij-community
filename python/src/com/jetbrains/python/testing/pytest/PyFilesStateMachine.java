/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Automata searches for file links and numbers.
 * Supports 2 modes: "quote-mode" (searches for strings in quotes) and "space mode" -- strings, separated by spaces (or start/end of line)
 * Create one providing appropriate flag.
 * <p/>
 * Call {@link #addChar(char, int)} or {@link #endLine()} when line ends and do the following:
 * if result is true, then machine found something. Use {@link #getFileAndLine()} to get result.
 * If false, provide next char
 *
 * @author Ilya.Kazakevich
 */
final class PyFilesStateMachine {
  private final boolean myQuoteMode; // In "quote" mode, spaces accepted
  private boolean myLookingForFile = true; // Looking for file if true , for line number if not
  private boolean myInProgress; // True if machine in progress, false if waits to start
  private boolean myWaitingForCharAfterSemicolon; // Semicolon entered, looking for next char
  private int myStart; // start position
  private final StringBuilder myFileName = new StringBuilder();
  private final StringBuilder myLineNumber = new StringBuilder();

  /**
   * @param quoteMode start machine in "quote mode" if true
   */
  PyFilesStateMachine(final boolean quoteMode) {
    myQuoteMode = quoteMode;

    if (!myQuoteMode) {
      myInProgress = true; // We can start machine if we do not need to wait for quote
    }
  }

  /**
   * @param charToCheck char to add to machine
   * @param charNumber  current position of char (to be checked by {@link #getStart()})
   * @return see class doc
   */
  boolean addChar(final char charToCheck, final int charNumber) {
    if (!myInProgress) {
      if ((charToCheck == '"' && myQuoteMode)) {
        myInProgress = true;
        myStart = charNumber;
      }
      return false;
    }

    if (myWaitingForCharAfterSemicolon) {
      // Previous step was ":", what is next?
      if (charToCheck == '/' || charToCheck == '\\') {
        // Slash? That is part of file
        myFileName.append(':');
        myFileName.append(charToCheck);
        myWaitingForCharAfterSemicolon = false;
        return false;
      }
      if (Character.isDigit(charToCheck)) {
        // Number? Line numbers started, file name found
        myLookingForFile = false;
        myLineNumber.append(charToCheck);
        myWaitingForCharAfterSemicolon = false;
        return false;
      }
      resetState();
      return false;
    }

    if (charToCheck == '"') {
      // If machine in quote mode, and file and line are already found, machine is finished!
      if (myQuoteMode && myFileName.length() > 0 && myLineNumber.length() > 0) {
        return true;
      }
      resetState();
      if (myQuoteMode) { // Relaunch process
        myInProgress = true;
        myStart = charNumber;
      }
      return false;
      // Can't handle quote in other modes
    }
    if (charToCheck == ' ') {
      if (myQuoteMode && myLookingForFile) { // Spaces in quote mode is legal file names
        myFileName.append(' ');
        return false;
      }
      if (!myQuoteMode && myFileName.length() > 0 && myLineNumber.length() > 0) { //In other modes in may indicate end
        return true;
      }
      resetState();
      if (!myQuoteMode) { // Relaunch process if we do not need quote
        myInProgress = true;
        myStart = charNumber;
      }
      return false;
    }
    if (Character.isLetter(charToCheck) || charToCheck == '/' || charToCheck == '\\' || charToCheck == '.' || charToCheck == '_' ||
        charToCheck == '-') {
      // Alpha symbols are or for file name
      if (myLookingForFile) {
        myFileName.append(charToCheck);
        return false;
      }
    }
    if (charToCheck == ':') {
      // Semicolon is ok as file name
      if (myLookingForFile) {
        myWaitingForCharAfterSemicolon = true;
        return false;
      }
    }
    if (Character.isDigit(charToCheck)) {
      if (!myLookingForFile) {
        myLineNumber.append(charToCheck);
        return false;
      }
      if (myFileName.length() == 0) {
        // Can't start with digit
        resetState();
        return false;
      }
      myFileName.append(charToCheck);
      return false;
    }
    // Any unknown char is success end if we already have file name and line number
    if (myFileName.length() > 0 && myLineNumber.length() > 0) {
      return true;
    }
    resetState();
    return false;
  }

  private void resetState() {
    myInProgress = false;
    myLookingForFile = true;
    myLineNumber.setLength(0);
    myFileName.setLength(0);
  }

  /**
   * @return when machine is in {@link PyFilesStateMachineState#FINISHED} use this method to get file and line number
   */
  @NotNull
  Pair<String, String> getFileAndLine() {
    return Pair.create(myFileName.toString(), myLineNumber.toString());
  }

  /**
   * @return position when this machine started
   */
  int getStart() {
    return myStart;
  }

  /**
   * To be called when line ends
   *
   * @return see class doc
   */
  boolean endLine() {
    if (myQuoteMode) {
      return false;
    }
    return myFileName.length() > 0 && myLineNumber.length() > 0;
  }
}
