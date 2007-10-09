/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

public interface InputValidator {
  /**
   * Checks whether the <code>inputString</code> is valid. It is invoked each time
   * input changes.
   */
  boolean checkInput(String inputString);

  /**
   * This method is invoked just before message dialog is closed with OK code.
   * If <code>false</code> is returned then then the message dialog will not be closed.
   */
  boolean canClose(String inputString);
}
