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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.wrq.rearranger.popup.RearrangerTreeNode;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.CommentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.regex.Matcher;

/**
 * Corresponds to Java syntactic items ("entries") like classes, fields, and methods.  A range entry
 * contains start and end Psi elements for each item (which could be identical), as well as modifier
 * flags and special flags to mark any miscellaneous text or comments that precede or follow the item.
 */
abstract public class RangeEntry implements PopupTreeRangeEntry {
// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + RangeEntry.class.getName());

  String myAlternateValue;
  final           String       myName;
  protected final PsiElement   myStart;
  protected final PsiElement   myEnd;
  private final   int          myModifiers;
  private final   String       myModifierString;
  private final   boolean      myFixedHeader;
  private final   boolean      myFixedTrailer;
  private final   String       myType;
  private         RuleInstance myMatchedRule;
  private         boolean      mySeparatorCommentPrecedes;

// --------------------------- CONSTRUCTORS ---------------------------

  public RangeEntry(@Nullable final PsiElement start,
                    @Nullable final PsiElement end,
                    final boolean fixedHeader,
                    final boolean fixedTrailer)
  {
    this(start, end, 0, "", "", "", fixedHeader, fixedTrailer);
  }

  RangeEntry(@Nullable final PsiElement start,
             @Nullable final PsiElement end,
             final int modifiers,
             final String modifierString,
             @Nullable final String name,
             final String type)
  {
    this(start, end, modifiers, modifierString, name, type, false, false);
  }

  private RangeEntry(@Nullable final PsiElement start,
                     @Nullable final PsiElement end,
                     final int modifiers,
                     final String modifierString,
                     @Nullable final String name,
                     final String type,
                     final boolean fixedHeader,
                     final boolean fixedTrailer)
  {
    myStart = start;
    myEnd = end;
    myModifiers = modifiers;
    myModifierString = modifierString;
    myFixedHeader = fixedHeader;
    myFixedTrailer = fixedTrailer;
    myName = name;
    myType = type;
    myAlternateValue = null;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public PsiElement getEnd() {
    return myEnd;
  }

  public RuleInstance getMatchedRule() {
    return myMatchedRule;
  }

  public void setMatchedRule(RuleInstance matchedRule) {
    this.myMatchedRule = matchedRule;
  }

  public int getModifiers() {
    return myModifiers;
  }

  public String getModifierString() {
    return myModifierString;
  }

  public String getName() {
    return myName;
  }

  public String getType() {
    return myType;
  }

  public PsiElement getStart() {
    return myStart;
  }

  public boolean isFixedHeader() {
    return myFixedHeader;
  }

  public boolean isFixedTrailer() {
    return myFixedTrailer;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public String toString() {
    String result = (myName == null ? "<unnamed>" : myName);
    if (myStart != null && myEnd != null) {
      result += "; range from " +
                myStart.toString() +
                " [" +
                myStart.getTextRange().getStartOffset() +
                "] to " +
                myEnd.toString() +
                " [" +
                myEnd.getTextRange().getEndOffset() +
                "]; modifiers=0x" +
                Integer.toHexString(myModifiers);
    }
    else {
      result += "; no start/end specified";
    }
    if (myFixedHeader) {
      result = "Header: " + result;
    }
    if (myFixedTrailer) {
      result = "Trailer: " + result;
    }
    if (myAlternateValue != null) {
      result += "; comments removed";
    }
    return result;
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface IPopupTreeRangeEntry ---------------------

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode myNode = new RearrangerTreeNode(this, myName);
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
      matcher.reset(myAlternateValue);
      boolean foundMatch = matcher.find();
      if (foundMatch) {
        LOG.debug("found comment pattern '" +
                  matcher.pattern().pattern().replaceAll("\n", "#") +
                  "' in '" +
                  myAlternateValue.replaceAll("\n", "#") +
                  "'");
        StringBuffer sb = new StringBuffer(myAlternateValue.length());
        do {
          boolean leadingNewlines = myStart.getTextRange().getStartOffset() > 0;
          matcher.appendReplacement(sb,
                                    leadingNewlines ? "\n" : "");
          foundMatch = matcher.find();
        }
        while (foundMatch);
        matcher.appendTail(sb);
        myAlternateValue = sb.toString();
        LOG.debug("RangeEntry alternateValue=" + myAlternateValue.replaceAll("\n", "#"));
      }
    }
  }

  protected void createAlternateValueString() {
    if (myAlternateValue != null) {
      return;
    }
    final StringBuilder sb = new StringBuilder(myEnd.getTextRange().getEndOffset() - myStart.getTextRange().getStartOffset());
    PsiElement e = myStart;
    while (e != null && e != myEnd) {
      if (e == myEnd.getParent()) {
        e = e.getFirstChild();
      }
      else {
        sb.append(e.getText());
        e = e.getNextSibling();
      }
    }
    if (e == myEnd) {
      sb.append(myEnd.getText());
    }
    myAlternateValue = sb.toString();
  }

  public void emit(Emitter emitter) {
    emitAllElements(emitter.getTextBuffer(), emitter.getDocument());
  }

  protected void emitAllElements(StringBuilder buffer, Document document) {
    if (myAlternateValue != null) {
      String result = myAlternateValue;
      if (mySeparatorCommentPrecedes) {
        // remove all leading blank lines.  The only blank lines we want are the ones explicitly appended
        // to the preceding separator comment.
        LOG.debug("emitAllElements: separator comment precedes " +
                  myName + "; original value=" +
                  myAlternateValue.replaceAll("\n", "#"));
        result = myAlternateValue.replaceFirst("\n[ \t\n]*\n", "\n");
        LOG.debug("emitAllElements: resulting value=" +
                  myAlternateValue.replaceAll("\n", "#"));
      }
      buffer.append(result);
    }
    else {
      PsiElement curr = myStart;
      while (curr != null && curr != myEnd) {
        if (curr == myEnd.getParent()) {
          curr = curr.getFirstChild();
        }
        else {
          emitElement(curr, buffer, document);
          curr = curr.getNextSibling();
        }
      }
      if (curr == myEnd) {
        emitElement(myEnd, buffer, document);
      }
    }
  }

  private static void emitElement(@NotNull final PsiElement curr, @NotNull final StringBuilder sb, @NotNull final Document document) {
    final TextRange range = curr.getTextRange();
    try {
      sb.append(document.getCharsSequence(), range.getStartOffset(), range.getEndOffset());
    }
    catch (ArrayIndexOutOfBoundsException oob) {
      LOG.error("internal error attempting to append text to document");
      LOG.error("document.getCharsSequence.length=" + document.getCharsSequence().length());
      LOG.error("document.getCharsSequence.toString().length=" + document.getCharsSequence().toString().length());
      LOG.error("document...toCharArray.length=" + document.getCharsSequence().toString().toCharArray().length);
      LOG.error("current PSI element=" + curr.toString());
      LOG.error("current PSI element text=" + curr.getText());
      LOG.error("current PSI element text range, start offset=" + range.getStartOffset());
      LOG.error("current PSI element text range, end offset=" + range.getEndOffset());
      LOG.error("current PSI element text range, length=" + range.getLength());
      LOG.error(oob);
      throw oob;
    }
  }

  public void setSeparatorCommentPrecedes(boolean precedes) {
    this.mySeparatorCommentPrecedes = precedes;
    LOG.debug("emitAllElements: set separator comment precedes " + myName);
  }
}

