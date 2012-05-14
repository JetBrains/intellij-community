/**
 * Id$
 *
 * Rearranger plugin for IntelliJ IDEA.
 *
 * Source code may be freely copied and reused.  Please copy credits, and send any bug fixes to the author.
 *
 * @author Dave Kriewall, WRQ, Inc.
 * January, 2004
 */
package com.wrq.rearranger;

/** Illustrates rearrangement by the plugin. */
abstract public class RearrangeMe {
  abstract boolean isGood(); // matches rule 12, "abstract methods"

  /** field1 is documented here.  This documentation moves with the field. */
  int field1;  // matches rule 3, "non-static fields ..."

  protected RearrangeMe(int field1) // Constructor matches rule 6
  {
    this.field1 = field1;
  }

  public final static int CONSTANT = 1; // matches rule 1, "... static fields"
}