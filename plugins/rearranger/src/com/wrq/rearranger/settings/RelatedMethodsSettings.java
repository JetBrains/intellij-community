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
package com.wrq.rearranger.settings;

import com.wrq.rearranger.settings.attributeGroups.RegexUtil;
import org.jdom.Element;

import java.util.List;

/** Contains settings regarding placement of related methods. */
public class RelatedMethodsSettings {
  private boolean moveExtractedMethods;
  private boolean belowFirstCaller;                      // false means below last caller
  private boolean depthFirstOrdering;                    // false means breadth first ordering

  public static final int INVOCATION_ORDER      = 0;
  public static final int RETAIN_ORIGINAL_ORDER = 1;
  public static final int ALPHABETICAL_ORDER    = 2;
  private int ordering;

  public static final int NON_PRIVATE_EXTRACTED_NEVER       = 0;
  public static final int NON_PRIVATE_EXTRACTED_ONE_CALLER  = 1;
  public static final int NON_PRIVATE_EXTRACTED_ANY_CALLERS = 2;

  private int nonPrivateTreatment;

  public static final int COMMENT_TYPE_TOP_LEVEL   = 0;
  public static final int COMMENT_TYPE_EACH_LEVEL  = 1;
  public static final int COMMENT_TYPE_EACH_METHOD = 2;
  public static final int COMMENT_TYPE_NEW_FAMILY  = 3;

  private int               commentType;
  private CommentRule       precedingComment;
  private CommentRule       trailingComment;
  private CommentFillString commentFillString;

  private RelatedByNameMethodsSettings rbnms;

  public RelatedByNameMethodsSettings getRbnms() {
    return rbnms;
  }

  public RelatedMethodsSettings() {
    belowFirstCaller = true;
    depthFirstOrdering = true;
    ordering = INVOCATION_ORDER;
    nonPrivateTreatment = NON_PRIVATE_EXTRACTED_ANY_CALLERS;
    commentType = COMMENT_TYPE_TOP_LEVEL;
    precedingComment = new CommentRule();
    trailingComment = new CommentRule();
    rbnms = new RelatedByNameMethodsSettings();
    commentFillString = new CommentFillString();
  }

  public boolean isMoveExtractedMethods() {
    return moveExtractedMethods;
  }

  public void setMoveExtractedMethods(boolean moveExtractedMethods) {
    this.moveExtractedMethods = moveExtractedMethods;
  }

  public boolean isBelowFirstCaller() {
    return belowFirstCaller;
  }

  public void setBelowFirstCaller(boolean belowFirstCaller) {
    this.belowFirstCaller = belowFirstCaller;
  }

  public boolean isDepthFirstOrdering() {
    return depthFirstOrdering;
  }

  public void setDepthFirstOrdering(boolean depthFirstOrdering) {
    this.depthFirstOrdering = depthFirstOrdering;
  }

  public int getOrdering() {
    return ordering;
  }

  public void setOrdering(int ordering) {
    this.ordering = ordering;
  }

  public int getNonPrivateTreatment() {
    return nonPrivateTreatment;
  }

  public void setNonPrivateTreatment(int nonPrivateTreatment) {
    this.nonPrivateTreatment = nonPrivateTreatment;
  }

  public int getCommentType() {
    return commentType;
  }

  public void setCommentType(int commentType) {
    this.commentType = commentType;
  }

  public CommentRule getPrecedingComment() {
    return precedingComment;
  }

  public void setPrecedingComment(CommentRule precedingComment) {
    this.precedingComment = precedingComment;
  }

  public CommentRule getTrailingComment() {
    return trailingComment;
  }

  public void setTrailingComment(CommentRule trailingComment) {
    this.trailingComment = trailingComment;
  }

  public CommentFillString getCommentFillString() {
    return commentFillString;
  }

  public boolean equals(Object object) {
    if (object instanceof RelatedMethodsSettings) {
      RelatedMethodsSettings rms = (RelatedMethodsSettings)object;
      return (rms.moveExtractedMethods == moveExtractedMethods &&
              rms.belowFirstCaller == belowFirstCaller &&
              rms.depthFirstOrdering == depthFirstOrdering &&
              rms.ordering == ordering &&
              rms.nonPrivateTreatment == nonPrivateTreatment &&
              rms.commentType == commentType &&
              rms.precedingComment.equals(precedingComment) &&
              rms.trailingComment.equals(trailingComment) &&
              rms.commentFillString.equals(commentFillString));
    }
    return false;
  }

  public RelatedMethodsSettings deepCopy() {
    RelatedMethodsSettings result = new RelatedMethodsSettings();
    result.moveExtractedMethods = moveExtractedMethods;
    result.belowFirstCaller = belowFirstCaller;
    result.depthFirstOrdering = depthFirstOrdering;
    result.ordering = ordering;
    result.nonPrivateTreatment = nonPrivateTreatment;
    result.commentType = commentType;
    result.precedingComment = (CommentRule)precedingComment.deepCopy();
    result.trailingComment = (CommentRule)trailingComment.deepCopy();
    result.commentFillString = commentFillString.deepCopy();
    return result;
  }

  public String toString() {
    return "moveExtractedMethods=" + moveExtractedMethods +
           ", belowFirstCaller=" + belowFirstCaller +
           ", depthFirstOrdering=" + depthFirstOrdering +
           ", ordering=" + ordering +
           ", non-private treatment=" + nonPrivateTreatment +
           ", comment type=" + commentType;
  }

  public void loadAttributes(final Element me) {
    moveExtractedMethods = RearrangerSettings.getBooleanAttribute(me, "moveExtractedMethods");
    belowFirstCaller = RearrangerSettings.getBooleanAttribute(me, "belowFirstCaller", true);
    depthFirstOrdering = RearrangerSettings.getBooleanAttribute(me, "depthFirstOrdering", true);
    ordering = RearrangerSettings.getIntAttribute(me,
                                                  "ordering",
                                                  INVOCATION_ORDER);
    nonPrivateTreatment = RearrangerSettings.getIntAttribute(me,
                                                             "nonPrivateTreatment",
                                                             NON_PRIVATE_EXTRACTED_ANY_CALLERS);
    commentType = RearrangerSettings.getIntAttribute(me,
                                                     "commentType",
                                                     COMMENT_TYPE_TOP_LEVEL);
    Element prc = me.getChild("PrecedingComment");
    if (prc != null) prc = prc.getChild("Comment");
    precedingComment = CommentRule.readExternal(prc);
    Element trc = me.getChild("TrailingComment");
    if (trc != null) trc = trc.getChild("Comment");
    trailingComment = CommentRule.readExternal(trc);
    commentFillString = CommentFillString.readExternal(me);
  }

  public static RelatedMethodsSettings readExternal(final Element item) {
    final RelatedMethodsSettings result = new RelatedMethodsSettings();
    result.loadAttributes(item);
    return result;
  }

  public final void writeExternal(final Element me) {
    me.setAttribute("moveExtractedMethods", Boolean.valueOf(moveExtractedMethods).toString());
    me.setAttribute("belowFirstCaller", Boolean.valueOf(belowFirstCaller).toString());
    me.setAttribute("depthFirstOrdering", Boolean.valueOf(depthFirstOrdering).toString());
    me.setAttribute("ordering", "" + ordering);
    me.setAttribute("nonPrivateTreatment", "" + nonPrivateTreatment);
    me.setAttribute("commentType", "" + commentType);
    Element prc = new Element("PrecedingComment");
    Element trc = new Element("TrailingComment");
    me.getChildren().add(prc);
    me.getChildren().add(trc);
    precedingComment.writeExternal(prc);
    trailingComment.writeExternal(trc);
    commentFillString.writeExternal(me);
  }

  public void addCommentPatternsToList(List<String> list) {
    addCommentPatternToList(precedingComment, list);
    addCommentPatternToList(trailingComment, list);
  }

  private void addCommentPatternToList(CommentRule comment, List<String> list) {
    String s = null;
    if (comment != null) {
      s = comment.getCommentText();
    }
    if (s != null && s.length() > 0) {
      String esc = RegexUtil.escape(s);
      String fsp = commentFillString.getFillStringPattern();

      s = RegexUtil.replaceAllFS(esc, fsp);
      s = s.replaceAll("%TL%", "[A-Za-z0-9_()]+");
      s = s.replaceAll("%MN%", "[A-Za-z0-9_()]+");
      s = s.replaceAll("%AM%", "[A-Za-z0-9_\\\\[\\\\],.]+");
      s = s.replaceAll("%LV%", "[0-9]+");
      list.add(s);
    }
  }
}
