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

package org.intellij.plugins.relaxNG.xml.dom;

import org.intellij.plugins.relaxNG.model.Pattern;

import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 16.08.2007
 */
public interface RngOpenPatterns extends RngDomElement, Pattern<XmlElement> {
  /**
   * Returns the list of element children.
   *
   * @return the list of element children.
   */
  @NotNull
  java.util.List<RngElement> getElements();

  /**
   * Adds new child to the list of element children.
   *
   * @return created child
   */
  RngElement addElement();

  /**
   * Returns the list of attribute children.
   *
   * @return the list of attribute children.
   */
  @NotNull
  List<RngAttribute> getAttributes();

  /**
   * Adds new child to the list of attribute children.
   *
   * @return created child
   */
  RngAttribute addAttribute();

  /**
   * Returns the list of group children.
   *
   * @return the list of group children.
   */
  @NotNull
  List<RngGroup> getGroups();

  /**
   * Adds new child to the list of group children.
   *
   * @return created child
   */
  RngGroup addGroup();

  /**
   * Returns the list of interleave children.
   *
   * @return the list of interleave children.
   */
  @NotNull
  List<RngInterleave> getInterleaves();

  /**
   * Adds new child to the list of interleave children.
   *
   * @return created child
   */
  RngInterleave addInterleave();

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

  /**
   * Returns the list of optional children.
   *
   * @return the list of optional children.
   */
  @NotNull
  List<RngOptional> getOptionals();

  /**
   * Adds new child to the list of optional children.
   *
   * @return created child
   */
  RngOptional addOptional();

  /**
   * Returns the list of zeroOrMore children.
   *
   * @return the list of zeroOrMore children.
   */
  @NotNull
  List<RngZeroOrMore> getZeroOrMores();

  /**
   * Adds new child to the list of zeroOrMore children.
   *
   * @return created child
   */
  RngZeroOrMore addZeroOrMore();

  /**
   * Returns the list of oneOrMore children.
   *
   * @return the list of oneOrMore children.
   */
  @NotNull
  List<RngOneOrMore> getOneOrMores();

  /**
   * Adds new child to the list of oneOrMore children.
   *
   * @return created child
   */
  RngOneOrMore addOneOrMore();

  /**
   * Returns the list of list children.
   *
   * @return the list of list children.
   */
  @NotNull
  List<List> getLists();

  /**
   * Adds new child to the list of list children.
   *
   * @return created child
   */
  List addList();

  /**
   * Returns the list of mixed children.
   *
   * @return the list of mixed children.
   */
  @NotNull
  List<RngMixed> getMixeds();

  /**
   * Adds new child to the list of mixed children.
   *
   * @return created child
   */
  RngMixed addMixed();

  /**
   * Returns the list of ref children.
   *
   * @return the list of ref children.
   */
  @NotNull
  List<RngRef> getRefs();

  /**
   * Adds new child to the list of ref children.
   *
   * @return created child
   */
  RngRef addRef();

  /**
   * Returns the list of parentRef children.
   *
   * @return the list of parentRef children.
   */
  @NotNull
  List<RngParentRef> getParentRefs();

  /**
   * Adds new child to the list of parentRef children.
   *
   * @return created child
   */
  RngParentRef addParentRef();

  /**
   * Returns the list of empty children.
   *
   * @return the list of empty children.
   */
  @NotNull
  List<RngEmpty> getEmpties();

  /**
   * Adds new child to the list of empty children.
   *
   * @return created child
   */
  RngEmpty addEmpty();

  /**
   * Returns the list of text children.
   *
   * @return the list of text children.
   */
  @NotNull
  List<RngText> getTexts();

  /**
   * Adds new child to the list of text children.
   *
   * @return created child
   */
  RngText addText();

  /**
   * Returns the list of value children.
   *
   * @return the list of value children.
   */
  @NotNull
  List<RngValue> getValues();

  /**
   * Adds new child to the list of value children.
   *
   * @return created child
   */
  RngValue addValue();

  /**
   * Returns the list of data children.
   *
   * @return the list of data children.
   */
  @NotNull
  List<RngData> getDatas();

  /**
   * Adds new child to the list of data children.
   *
   * @return created child
   */
  RngData addData();

  /**
   * Returns the list of notAllowed children.
   *
   * @return the list of notAllowed children.
   */
  @NotNull
  List<RngNotAllowed> getNotAlloweds();

  /**
   * Adds new child to the list of notAllowed children.
   *
   * @return created child
   */
  RngNotAllowed addNotAllowed();

  /**
   * Returns the list of externalRef children.
   *
   * @return the list of externalRef children.
   */
  @NotNull
  List<RngExternalRef> getExternalRefs();

  /**
   * Adds new child to the list of externalRef children.
   *
   * @return created child
   */
  RngExternalRef addExternalRef();

  /**
   * Returns the list of grammar children.
   *
   * @return the list of grammar children.
   */
  @NotNull
  List<RngGrammar> getGrammars();

  /**
   * Adds new child to the list of grammar children.
   *
   * @return created child
   */
  RngGrammar addGrammar();
}
