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
package com.intellij.openapi.vcs.annotate;

/**
 * Represents one part of a line annotation which is shown in the editor when the "Annotate"
 * action is invoked. Classes implementing this interface can also implement
 * {@link com.intellij.openapi.editor.EditorGutterAction} to handle clicks on the annotation. 
 *
 * @see FileAnnotation#getAspects()
 */
public interface LineAnnotationAspect {
  /**
   * Get annotation text for the spcific line number
   * @param lineNumber the line number to query
   * @return the annotation text 
   */
  String getValue(int lineNumber);
}
