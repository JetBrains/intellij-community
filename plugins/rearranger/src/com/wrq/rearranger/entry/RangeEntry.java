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
package com.wrq.rearranger.entry;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.wrq.rearranger.popup.RearrangerTreeNode;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.CommentUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.regex.Matcher;

/**
 * Corresponds to Java syntactic items ("entries") like classes, fields, and methods.  A range entry
 * contains start and end Psi elements for each item (which could be identical), as well as modifier
 * flags and special flags to mark any miscellaneous text or comments that precede or follow the item.
 */
abstract public class RangeEntry implements IPopupTreeRangeEntry {
// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + RangeEntry.class.getName());
  String alternateValue;
  final           String        name;
  protected final PsiElement    start;
  protected final PsiElement    end;
  private final   int           modifiers;
  private final   String        modifierString;
  private final   boolean       fixedHeader;
  private final   boolean       fixedTrailer;
  private final   String        type;
  private RuleInstance myMatchedRule;
  private         boolean       separatorCommentPrecedes;

// --------------------------- CONSTRUCTORS ---------------------------

  public RangeEntry(final PsiElement start,
                    final PsiElement end,
                    final boolean fixedHeader,
                    final boolean fixedTrailer)
  {
    this(start, end, 0, "", "", "", fixedHeader, fixedTrailer);
  }

  RangeEntry(final PsiElement start,
             final PsiElement end,
             final int modifiers,
             final String modifierString,
             final String name,
             final String type)
  {
    this(start, end, modifiers, modifierString, name, type, false, false);
  }

  private RangeEntry(final PsiElement start,
                     final PsiElement end,
                     final int modifiers,
                     final String modifierString,
                     final String name,
                     final String type,
                     final boolean fixedHeader,
                     final boolean fixedTrailer)
  {
    this.start = start;
    this.end = end;
    this.modifiers = modifiers;
    this.modifierString = modifierString;
    this.fixedHeader = fixedHeader;
    this.fixedTrailer = fixedTrailer;
    this.name = name;
    this.type = type;
    alternateValue = null;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public PsiElement getEnd() {
    return end;
  }

  public RuleInstance getMatchedRule() {
    return myMatchedRule;
  }

  public void setMatchedRule(RuleInstance matchedRule) {
    this.myMatchedRule = matchedRule;
  }

  public int getModifiers() {
    return modifiers;
  }

  public String getModifierString() {
    return modifierString;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public PsiElement getStart() {
    return start;
  }

  public boolean isFixedHeader() {
    return fixedHeader;
  }

  public boolean isFixedTrailer() {
    return fixedTrailer;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public String toString() {
    String result = (name == null ? "<unnamed>" : name);
    if (start != null && end != null) {
      result += "; range from " +
                start.toString() +
                " [" +
                start.getTextRange().getStartOffset() +
                "] to " +
                end.toString() +
                " [" +
                end.getTextRange().getEndOffset() +
                "]; modifiers=0x" +
                Integer.toHexString(modifiers);
    }
    else {
      result += "; no start/end specified";
    }
    if (fixedHeader) {
      result = "Header: " + result;
    }
    if (fixedTrailer) {
      result = "Trailer: " + result;
    }
    if (alternateValue != null) {
      result += "; comments removed";
    }
    return result;
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface IPopupTreeRangeEntry ---------------------

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode myNode = new RearrangerTreeNode(this, name);
    parent.add(myNode);
    return myNode;
  }

// -------------------------- OTHER METHODS --------------------------

  public void checkForComment() {
    if (CommentUtil.getCommentMatchers().size() == 0) {
      return;
    }
    createAlternateValueString();
    /**
     * for each separator comment specified by the user, check to see if this comment matches.
     */
    for (Matcher matcher : CommentUtil.getCommentMatchers()) {
      matcher.reset(alternateValue);
      boolean foundMatch = matcher.find();
      if (foundMatch) {
        LOG.debug("found comment pattern '" +
                  matcher.pattern().pattern().replaceAll("\n", "#") +
                  "' in '" +
                  alternateValue.replaceAll("\n", "#") +
                  "'");
        StringBuffer sb = new StringBuffer(alternateValue.length());
        do {
          boolean leadingNewlines = start.getTextRange().getStartOffset() > 0;
          matcher.appendReplacement(sb,
                                    leadingNewlines ? "\n" : "");
          foundMatch = matcher.find();
        }
        while (foundMatch);
        matcher.appendTail(sb);
        alternateValue = sb.toString();
        LOG.debug("RangeEntry alternateValue=" + alternateValue.replaceAll("\n", "#"));
      }
    }
  }

  protected void createAlternateValueString() {
    if (alternateValue != null) {
      return;
    }
    final StringBuffer sb = new StringBuffer(
      end.getTextRange().getEndOffset() -
      start.getTextRange().getStartOffset()
    );
    PsiElement e = start;
    while (e != null && e != end) {
      if (e == end.getParent()) {
        e = e.getFirstChild();
      }
      else {
        sb.append(e.getText());
        e = e.getNextSibling();
      }
    }
    if (e == end) {
      sb.append(end.getText());
    }
    alternateValue = sb.toString();
  }

  public void emit(Emitter emitter) {
    emitAllElements(emitter.getStringBuffer(), emitter.getDocument());
  }

  protected void emitAllElements(StringBuffer sb, Document document) {
    if (alternateValue != null) {
      String result = alternateValue;
      if (separatorCommentPrecedes) {
        // remove all leading blank lines.  The only blank lines we want are the ones explicitly appended
        // to the preceding separator comment.
        LOG.debug("emitAllElements: separator comment precedes " +
                  name + "; original value=" +
                  alternateValue.replaceAll("\n", "#"));
        result = alternateValue.replaceFirst("\n[ \t\n]*\n", "\n");
        LOG.debug("emitAllElements: resulting value=" +
                  alternateValue.replaceAll("\n", "#"));
      }
      sb.append(result);
    }
    else {
      PsiElement curr = start;
      while (curr != null && curr != end) {
        if (curr == end.getParent()) {
          curr = curr.getFirstChild();
        }
        else {
          emitElement(curr, sb, document);
          curr = curr.getNextSibling();
        }
      }
      if (curr == end) {
        emitElement(end, sb, document);
      }
    }
  }

  private void emitElement(final PsiElement curr,
                           final StringBuffer sb,
                           final Document document)
  {
    try {
      sb.append(
        document.getCharsSequence().toString().toCharArray(),  // for Irida (builds 3185 etc).
        curr.getTextRange().getStartOffset(),
        curr.getTextRange().getEndOffset() - curr.getTextRange().getStartOffset()
      );
    }
    catch (ArrayIndexOutOfBoundsException oob) {
      LOG.error("internal error attempting to append text to document");
      LOG.error("document.getCharsSequence.length=" + document.getCharsSequence().length());
      LOG.error("document.getCharsSequence.toString().length=" + document.getCharsSequence().toString().length());
      LOG.error("document...toCharArray.length=" + document.getCharsSequence().toString().toCharArray().length);
      LOG.error("current PSI element=" + curr.toString());
      LOG.error("current PSI element text=" + curr.getText());
      LOG.error("current PSI element text range, start offset=" + curr.getTextRange().getStartOffset());
      LOG.error("current PSI element text range, end offset=" + curr.getTextRange().getEndOffset());
      LOG.error("current PSI element text range, length=" + curr.getTextRange().getLength());
      LOG.error(oob);
      throw oob;
    }
  }

  public void setSeparatorCommentPrecedes(boolean precedes) {
    this.separatorCommentPrecedes = precedes;
    LOG.debug("emitAllElements: set separator comment precedes " + name);
  }
}

