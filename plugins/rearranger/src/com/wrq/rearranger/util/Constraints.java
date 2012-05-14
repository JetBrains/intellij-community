/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.util;

import java.awt.*;

/** Utility class for performing common operations on GridBagConstraints. */
public class Constraints
  extends GridBagConstraints
{
  boolean newRow;

  /**
   * Constructs a new Constraints object and initializes a new row.<br>
   * Side effects are:
   * <pre>
   *    gridy = 0
   *    gridx = 0
   *    gridheight = 1
   *    gridwidth = 1
   *    weightx = 0
   *    weighty = 0
   *    fill = NONE
   *    anchor = WEST
   * </pre>
   */
  public Constraints() {
    fill = GridBagConstraints.NONE;
    gridwidth = 1;
    gridheight = 1;
    anchor = GridBagConstraints.WEST;
    weightx = 0.0d;
    weighty = 0.0d;
    gridx = gridy = 0;
    newRow = true;
    newRow();
  }

  /**
   * Constructs a new Constraints object and initializes a new row.<br>
   * Side effects are:
   * <pre>
   *    gridy = 0
   *    gridx = 0
   *    gridheight = 1
   *    gridwidth = 1
   *    weightx = 0
   *    weighty = 0
   *    fill = NONE
   *    anchor = specified parameter
   * </pre>
   */
  public Constraints(int anchor) {
    this();
    this.anchor = anchor;
  }

  /**
   * Prepares for adding components to a new row.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not already at the beginning of a new row)
   *    gridx = 0
   *    gridheight = 1
   *    weighty = 0
   * </pre>
   */
  public void newRow() {
    if (!newRow) {
      gridy++;
    }
    gridx = 0;
    gridheight = 1;
    weighty = 0;
    newRow = true;
  }

  /**
   * Prepares for adding components to a new row, which is weighted.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not the first row)
   *    gridx = 0
   *    gridheight = 1
   *    weighty = supplied parameter
   * </pre>
   */
  public void weightedNewRow(double weight) {
    newRow();
    weighty = weight;
  }

  /**
   * Prepares for adding components to a new row, which is weighted.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not the first row)
   *    gridx = 0
   *    gridheight = 1
   *    weighty = 1
   * </pre>
   */
  public void weightedNewRow() {
    weightedNewRow(1);
  }

  /**
   * Prepares for adding components to a new row which will be the last row.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not the first row)
   *    gridx = 0
   *    gridheight = REMAINDER
   *    weighty = 0
   * </pre>
   */
  public void lastRow() {
    newRow();
    gridheight = REMAINDER;
  }

  /**
   * Prepares for adding components to a new weightd row which will be the last row.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not the first row)
   *    gridx = 0
   *    gridheight = REMAINDER
   *    weighty = supplied parameter
   * </pre>
   */
  public void weightedLastRow(double weight) {
    lastRow();
    weighty = weight;
  }

  /**
   * Prepares for adding components to a new weightd row which will be the last row.<br>
   * Side effects are:
   * <pre>
   *    gridy++ (if not the first row)
   *    gridx = 0
   *    gridheight = REMAINDER
   *    weighty = 1
   * </pre>
   */
  public void weightedLastRow() {
    weightedLastRow(1);
  }

  /**
   * Automatically starts a new row if not explicitly done already or if columns have been added since the last
   * newRow() call.<br>
   * Side effects are:
   * <pre>
   *    gridx = 0
   *    newRow = false
   *    gridwidth = 1
   *    weightx = 0
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints firstCol() {
    if (!newRow) {
      newRow();
    }
    gridx = 0;
    newRow = false;
    gridwidth = 1;
    weightx = 0;
    return this;
  }

  /**
   * Automatically starts a new weighted row if not explicitly done already or if columns have been added since the last
   * newRow() call.<br>
   * Side effects are:
   * <pre>
   *    gridx = 0
   *    newRow = false
   *    gridwidth = 1
   *    weightx = 1
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints weightedFirstCol() {
    firstCol();
    weightx = 1;
    return this;
  }

  /**
   * Prepares to add a component to the next column.  If this is the first column in the row,
   * gridx will be zero (not one).<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = 1
   *    weightx = 0
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints nextCol() {
    gridx = newRow ? 0 : gridx + 1;
    newRow = false;
    gridwidth = 1;
    weightx = 0;
    return this;
  }

  /**
   * Prepares to add a weighted component to the next column.<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = 1
   *    weightx = supplied parameter
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints weightedNextCol(double weight) {
    nextCol();
    weightx = weight;
    return this;
  }

  /**
   * Prepares to add a weighted component to the next column.<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = 1
   *    weightx = 1
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints weightedNextCol() {
    return weightedNextCol(1);
  }

  /**
   * Prepares to add a component to the last column.<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = REMAINDER
   *    weightx = 0
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints lastCol() {
    nextCol();
    gridwidth = REMAINDER;
    return this;
  }

  /**
   * Prepares to add a weighted component to the last column.<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = REMAINDER
   *    weightx = supplied parameter
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints weightedLastCol(double weight) {
    weightedNextCol(weight);
    gridwidth = REMAINDER;
    return this;
  }

  /**
   * Prepares to add a weighted component to the last column.<br>
   * Side effects are:
   * <pre>
   *    gridx = gridx + 1 (or zero, if this is the beginning of a new row)
   *    newRow = false
   *    gridwidth = REMAINDER
   *    weightx = 1
   * </pre>
   *
   * @return this Constraints object
   */
  public Constraints weightedLastCol() {
    weightedLastCol(1);
    gridwidth = REMAINDER;
    return this;
  }
}
