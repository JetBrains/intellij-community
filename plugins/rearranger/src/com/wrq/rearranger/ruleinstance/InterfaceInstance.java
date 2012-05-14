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
package com.wrq.rearranger.ruleinstance;

import com.wrq.rearranger.entry.MethodEntry;
import com.wrq.rearranger.popup.IFilePopupEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.InterfaceAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Corresponds to a single interface with one or more implementing methods. */
public class InterfaceInstance
  implements IFilePopupEntry
{
// ------------------------------------------------------- FIELDS ------------------------------------------------------

  private String interfaceName;

  public String getInterfaceName() {
    return interfaceName;
  }

  private List<MethodEntry>   methods;
  private InterfaceAttributes rule;

// ---------------------------------------------------- CONSTRUCTORS ---------------------------------------------------

  public InterfaceInstance(String interfaceName, InterfaceAttributes rule) {
    this.interfaceName = interfaceName;
    methods = new ArrayList<MethodEntry>();
    this.rule = rule;
  }

// ---------------------------------------------- GETTER / SETTER METHODS ----------------------------------------------

  public String[] getAdditionalIconNames() {
    return null;
  }

  public String getTypeIconName() {
    return null;
  }

// ------------------------------------------------- INTERFACE METHODS -------------------------------------------------

// --------------------------------------------- Interface IFilePopupEntry ---------------------------------------------

  public JLabel getPopupEntryText(RearrangerSettings settings) {
    JLabel label = new JLabel("methods of interface " + interfaceName);
    Font font = label.getFont().deriveFont(Font.ITALIC);
    label.setFont(font);
    return label;
  }

// --------------------------------------------------- OTHER METHODS ---------------------------------------------------

  /**
   * add the method to the list of methods, in the order specified by the configuration settings (alphabetical,
   * in order declared in the interface, or in order encountered).
   *
   * @param entry method to add.
   */
  void addMethod(MethodEntry entry) {
    switch (rule.getMethodOrder()) {
      case InterfaceAttributes.METHOD_ORDER_ALPHABETICAL:
        entry.insertAlphabetically(methods);
        break;
      case InterfaceAttributes.METHOD_ORDER_ENCOUNTERED:
        methods.add(entry);
        break;
      case InterfaceAttributes.METHOD_ORDER_INTERFACE_ORDER:
        entry.insertInterfaceOrder(methods);
        break;
    }
  }

  public void addToPopupTree(DefaultMutableTreeNode node, RearrangerSettings settings) {
    /**
     * if we are supposed to show rules, create a node for the rule and put its contents below.
     * Otherwise, delegate to each of the matches.
     */
    DefaultMutableTreeNode top = node;
    if (settings.isShowRules()) {
      top = new DefaultMutableTreeNode(this);
      node.add(top);
    }
    /**
     * append each of the matches to the top level.
     */
    for (MethodEntry entry : methods) {
      entry.addToPopupTree(top, settings);
    }
  }

  void emit(Emitter emitter) {
    StringBuffer sb = emitter.getStringBuffer();
    String commentString = expandComment(rule.getPrecedingComment());
    if (commentString.length() > 0) {
      sb.append("\n");
      sb.append(commentString);
    }
    for (MethodEntry rangeEntry : methods) {
      rangeEntry.emit(emitter);
    }
    commentString = expandComment(rule.getTrailingComment());
    if (commentString.length() > 0) {
      sb.append("\n");
      sb.append(commentString);
    }
  }

  private String expandComment(CommentRule comment) {
    if (comment == null || comment.getCommentText() == null) return "";
    comment.setCommentFillString(rule.getCommentFillString());
    String result = comment.getCommentText().replaceAll("%IF%", interfaceName);
    result = rule.getCommentFillString().getExpandedCommentText(result);
    return result;
  }
}

