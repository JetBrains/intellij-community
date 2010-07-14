/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Generated on Fri Aug 10 16:21:20 CEST 2007
// DTD/Schema  :    http://relaxng.org/ns/structure/1.0

package org.intellij.plugins.relaxNG.xml.dom.names;

import org.intellij.plugins.relaxNG.xml.dom.RngChoice;
import org.intellij.plugins.relaxNG.xml.dom.RngDomElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * http://relaxng.org/ns/structure/1.0:open-name-classes interface.
 */
public interface OpenNameClasses extends RngDomElement {

  /**
   * Returns the value of the name child.
   *
   * @return the value of the name child.
   */
  @NotNull
  Name getName();

  /**
   * Returns the value of the anyName child.
   *
   * @return the value of the anyName child.
   */
  @NotNull
  AnyName getAnyName();


  /**
   * Returns the value of the nsName child.
   *
   * @return the value of the nsName child.
   */
  @NotNull
  NsName getNsName();


//  /**
//   * Returns the value of the choice child.
//   *
//   * @return the value of the choice child.
//   */
//  @NotNull
//  RngChoice getChoice();

  /**
   * Returns the list of choice children.
   *
   * @return the list of choice children.
   */
  @NotNull
  List<RngChoice> getChoices();

  /**
   * Adds new child to the list of choice children.
   *
   * @return created child
   */
  RngChoice addChoice();
}
