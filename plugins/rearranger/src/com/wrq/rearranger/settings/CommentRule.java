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

import com.wrq.rearranger.entry.PopupTreeRangeEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.popup.FilePopupEntry;
import com.wrq.rearranger.popup.RearrangerTreeNode;
import com.wrq.rearranger.ruleinstance.CommentRuleInstanceFactory;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.attributeGroups.AttributeGroup;
import com.wrq.rearranger.settings.attributeGroups.RegexUtil;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/** Handles insertion of comment separators between sections of the rearranged file. */
public final class CommentRule
  implements AttributeGroup,
             PopupTreeRangeEntry,
             FilePopupEntry
{
// ------------------------------------------------------- FIELDS ------------------------------------------------------

  public static final int EMIT_ALWAYS                           = 0;
  public static final int EMIT_IF_ITEMS_MATCH_PRECEDING_RULE    = 1;
  public static final int EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE   = 2;
  public static final int EMIT_IF_ITEMS_MATCH_SURROUNDING_RULES = 3;

  private String commentText;

  public final String getCommentText() {
    return commentText;
  }

  public final void setCommentText(final String commentText) {
    StringBuffer sb = new StringBuffer(commentText);
    while (sb.length() > 0 &&
           Character.isWhitespace(sb.charAt(sb.length() - 1)) &&
           sb.charAt(sb.length() - 1) != '\n')
    {
      sb.setLength(sb.length() - 1);
    }
    this.commentText = sb.toString();
  }

  private int emitCondition;

  public final int getEmitCondition() {
    return emitCondition;
  }

  public final void setEmitCondition(final int emitCondition) {
    this.emitCondition = emitCondition;
  }

  private int     myNPrecedingRulesToMatch;                    // default 1
  private int     myNSubsequentRulesToMatch;                   // default 1
  private boolean myAllPrecedingRules;                         // if false, ANY preceding rules

  public final boolean isAllPrecedingRules() {
    return myAllPrecedingRules;
  }

  public final void setAllPrecedingRules(final boolean allPrecedingRules) {
    this.myAllPrecedingRules = allPrecedingRules;
  }

  private boolean myAllSubsequentRules;                        // if false, ANY subsequent rules

  public final boolean isAllSubsequentRules() {
    return myAllSubsequentRules;
  }

  public final void setAllSubsequentRules(final boolean allSubsequentRules) {
    this.myAllSubsequentRules = allSubsequentRules;
  }

  private CommentFillString commentFillString;

  public CommentFillString getCommentFillString() {
    return commentFillString;
  }

  public void setCommentFillString(CommentFillString commentFillString) {
    this.commentFillString = commentFillString;
  }

// --------------------------------------------------- STATIC METHODS --------------------------------------------------

  public static CommentRule readExternal(final Element item) {
    final CommentRule result = new CommentRule();

    final Attribute attr = RearrangerSettings.getAttribute(item, "commentText");
    // backward compatibility.  Comment text was formerly saved as an attribute.
    // Newline characters were lost this way.  Now comment text is saved as element text.
    // If there's no text but there is an attribute, load the old format.  It will be
    // saved as element text next time IDEA is exited.
    // Turns out that IDEA strips newlines in the element text.  Maybe it's due to document
    // formatting options.  In any case, we have to escape newline characters.
    String text = item.getText();
    if (text == null || text.length() == 0) {
      result.commentText = (attr == null ? "" : ((java.lang.String)attr.getValue()));
    }
    else {
      result.commentText = unescape(text);
    }
    result.emitCondition = RearrangerSettings.getIntAttribute(item, "condition", 0);
    result.myNPrecedingRulesToMatch = RearrangerSettings.getIntAttribute(item, "nPrecedingRulesToMatch", 1);
    result.myNSubsequentRulesToMatch = RearrangerSettings.getIntAttribute(item, "nSubsequentRulesToMatch", 1);
    result.myAllPrecedingRules = RearrangerSettings.getBooleanAttribute(item, "allPrecedingRules", true);
    result.myAllSubsequentRules = RearrangerSettings.getBooleanAttribute(item, "allSubsequentRules", true);
    result.commentFillString = CommentFillString.readExternal(item);
    return result;
  }

  public static String unescape(String text) {
    StringBuffer s = new StringBuffer(text);
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\\' && (i + 1 < s.length())) {
        switch (s.charAt(i + 1)) {
          case 'n':
            s.setCharAt(i, '\n');
            s.deleteCharAt(i + 1);
            break;
          case '\\':
            s.deleteCharAt(i + 1);
            break;
          case 't':
            s.setCharAt(i, '\t');
            s.deleteCharAt(i + 1);
            break;
          case ' ':
            s.deleteCharAt(i);
            break;
        }
      }
    }
    return s.toString();
  }

  public static String escape(String s) {
    StringBuffer sb = new StringBuffer(s);
    // for reasons given in readExternal(), escape certain characters in the comment text.
    for (int i = 0; i < sb.length(); i++) {
      switch (sb.charAt(i)) {
        case '\\':
        case ' ':
          sb.insert(i, '\\');
          i++;
          break;
        case '\n':
          sb.replace(i, i + 1, "\\n");
          i++;
          break;
        case '\t':
          sb.replace(i, i + 1, "\\t");
          i++;
          break;
      }
    }
    return sb.toString();
  }

// ---------------------------------------------------- CONSTRUCTORS ---------------------------------------------------

  public CommentRule() {
    commentText = "";
    emitCondition = EMIT_ALWAYS;
    myNPrecedingRulesToMatch = 1;
    myNSubsequentRulesToMatch = 1;
    myAllPrecedingRules = true;
    myAllSubsequentRules = true;
    commentFillString = new CommentFillString();
  }

// ---------------------------------------------- GETTER / SETTER METHODS ----------------------------------------------

  public String[] getAdditionalIconNames() {
    return null;
  }

  public int getCommentCount() {
    return (commentText != null && commentText.length() > 0) ? 1 : 0;
  }

  public final JPanel getCommentPanel() {
    final JPanel commentPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    commentPanel.setBorder(border);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.insets = new Insets(4, 4, 4, 0);
    final JLabel emitLabel = new JLabel("Emit comment:");
    final JRadioButton alwaysButton = new JRadioButton("Always"),
      whenButton = new JRadioButton("When");
    final ButtonGroup group = new ButtonGroup();
    group.add(alwaysButton);
    group.add(whenButton);
    if (emitCondition == 0) {
      alwaysButton.setSelected(true);
    }
    else {
      whenButton.setSelected(true);
    }
    commentPanel.add(emitLabel, constraints.weightedLastCol());
    constraints.newRow();
    constraints.insets = new Insets(0, 14, 0, 0);
    commentPanel.add(alwaysButton, constraints.weightedLastCol());
    constraints.newRow();
    commentPanel.add(whenButton, constraints.weightedLastCol());
    constraints.insets = new Insets(0, 24, 0, 4);
    constraints.newRow();
    final JPanel anyAllPreviousPanel = new AnyAllPanel(true);
    commentPanel.add(anyAllPreviousPanel, constraints.weightedLastCol());
    constraints.newRow();
    constraints.insets = new Insets(0, 24, 10, 4);
    final JPanel anyAllSubsequentPanel = new AnyAllPanel(false);
    commentPanel.add(anyAllSubsequentPanel, constraints.weightedLastCol());
    anyAllPreviousPanel.setEnabled(emitCondition > 0);
    anyAllSubsequentPanel.setEnabled(emitCondition > 0);
    final JLabel commentLabel = new JLabel("Comment separator text:");
    constraints.insets = new Insets(4, 4, 4, 4);
    constraints.newRow();
    commentPanel.add(commentLabel, constraints.weightedLastCol());
    final JTextArea commentArea = new JTextArea(4, 40);
    commentArea.setText(getCommentText());
    constraints.weightedNewRow();
    final JScrollPane scrollPane = new JScrollPane(commentArea);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    commentPanel.add(scrollPane, constraints.weightedLastCol());
    constraints.newRow();
    final JPanel cfsPanel = commentFillString.getCommentFillStringPanel();
    final JLabel fillStringLabel = new JLabel("Fill string:");
    fillStringLabel.setToolTipText("Occurrences of %FS% in the comment separator text will be replaced with equal \n" +
                                   "amounts of the fill string, replicated to make the comment end at the specified column");
    commentPanel.add(cfsPanel, constraints.weightedLastCol());

    alwaysButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (alwaysButton.isSelected()) {
          emitCondition = EMIT_ALWAYS;
          anyAllPreviousPanel.setEnabled(false);
          anyAllSubsequentPanel.setEnabled(false);
        }
      }
    });
    whenButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (whenButton.isSelected()) {
          anyAllPreviousPanel.setEnabled(true);
          anyAllSubsequentPanel.setEnabled(true);
        }
      }
    });
    commentArea.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(final DocumentEvent e) {
        setCommentText(commentArea.getText());
      }

      public void insertUpdate(final DocumentEvent e) {
        setCommentText(commentArea.getText());
      }

      public void removeUpdate(final DocumentEvent e) {
        setCommentText(commentArea.getText());
      }
    });
    return commentPanel;
  }

  public String getDescriptiveString() {
    final StringBuffer sb = new StringBuffer(commentText.trim());
    if (sb.length() > 60) {
      // attempt to shorten by removing duplicate (well, quadruplicate) characters.
      for (int i = 0; i < sb.length() - 4; i++) {
        if (sb.charAt(i) == sb.charAt(i + 1) &&
            sb.charAt(i + 1) == sb.charAt(i + 2) &&
            sb.charAt(i + 2) == sb.charAt(i + 3))
        {
          // we have at least 4 characters in a row.  Shorten the entire sequence to
          // a length of 4 characters and append the length.
          int j = 0;
          while (i + 4 < sb.length() && sb.charAt(i) == sb.charAt(i + 4)) {
            j++;
            sb.deleteCharAt(i + 4);
          }
          if (j > 3) {
            sb.insert(i + 4, "<" + j + ">");
          }
          else {
            // compression is quite small, so just output the actual characters.
            while (j-- > 0) {
              sb.insert(i + 4, sb.charAt(i));
            }
          }
        }
      }
    }
    if (sb.length() > 60) {
      sb.delete(60, sb.length());
      sb.append("...");
    }
    String condition = "";
    switch (emitCondition) {
      case EMIT_ALWAYS:
        break;
      case EMIT_IF_ITEMS_MATCH_PRECEDING_RULE:
        condition = " when " + precedingRuleString();
        break;
      case EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE:
        condition = " when " + subsequentRuleString();
        break;
      case EMIT_IF_ITEMS_MATCH_SURROUNDING_RULES:
        condition = " when " + precedingRuleString() + ", and " + subsequentRuleString();
    }
    return ("Comment (" + sb.toString() + ")" + condition);
  }

  private String precedingRuleString() {
    return (myNPrecedingRulesToMatch == 1) ? "preceding rule is matched" :
           (myAllPrecedingRules ? "all" : "any") +
           " of preceding " + (myNPrecedingRulesToMatch) + " rules are matched";
  }

  private String subsequentRuleString() {
    return (myNSubsequentRulesToMatch == 1) ? "subsequent rule is matched" :
           (myAllSubsequentRules ? "all" : "any") +
           " of subsequent " + (myNSubsequentRulesToMatch) + " rules are matched";
  }

  public final String getExpandedCommentText() {
    return commentFillString.getExpandedCommentText(commentText);
  }

  private String getName() {
    return commentText.trim();
  }

  public int getPriority() {
    return -1;  // Comments never match anything, so any priority will do.
  }

  public void setPriority(int priority) {
    // does nothing
  }

  public String getTypeIconName() {
    return "nodes/J2eeParameter";
  }

  public final boolean isAlphabetize() {
    throw new UnsupportedOperationException("CommentRule.isAlphabetize() not implemented");
  }

  public final void setNPrecedingRulesToMatch(final int nPrecedingRulesToMatch) {
    this.myNPrecedingRulesToMatch = nPrecedingRulesToMatch;
  }

  public final void setNSubsequentRulesToMatch(final int nSubsequentRulesToMatch) {
    this.myNSubsequentRulesToMatch = nSubsequentRulesToMatch;
  }

// ------------------------------------------------- CANONICAL METHODS -------------------------------------------------

  public final boolean equals(final Object object) {
    if (!(object instanceof CommentRule)) return false;
    final CommentRule c = (CommentRule)object;
    return commentText.equals(c.commentText) &&
           emitCondition == c.emitCondition &&
           myNPrecedingRulesToMatch == c.myNPrecedingRulesToMatch &&
           myNSubsequentRulesToMatch == c.myNSubsequentRulesToMatch &&
           myAllPrecedingRules == c.myAllPrecedingRules &&
           myAllSubsequentRules == c.myAllSubsequentRules &&
           commentFillString.equals(c.commentFillString);
  }

  public final String toString() {
    return getDescriptiveString();
  }

// ================================================= INTERFACE METHODS =================================================

// ---------------------------------------------- Interface AttributeGroup ---------------------------------------------

  @NotNull
  public final /*CommentRule*/AttributeGroup deepCopy() {
    final CommentRule comment = new CommentRule();
    comment.commentText = commentText;
    comment.emitCondition = emitCondition;
    comment.myNPrecedingRulesToMatch = myNPrecedingRulesToMatch;
    comment.myNSubsequentRulesToMatch = myNSubsequentRulesToMatch;
    comment.myAllPrecedingRules = myAllPrecedingRules;
    comment.myAllSubsequentRules = myAllSubsequentRules;
    comment.commentFillString = commentFillString.deepCopy();
    return comment;
  }

  public final void writeExternal(@NotNull final Element parent) {
    final Element me = new Element("Comment");
    me.setText(escape(commentText));
    me.setAttribute("condition", "" + emitCondition);
    me.setAttribute("nPrecedingRulesToMatch", "" + myNPrecedingRulesToMatch);
    me.setAttribute("nSubsequentRulesToMatch", "" + myNSubsequentRulesToMatch);
    me.setAttribute("allPrecedingRules", "" + myAllPrecedingRules);
    me.setAttribute("allSubsequentRules", "" + myAllSubsequentRules);
    commentFillString.writeExternal(me);
    parent.getChildren().add(me);
  }

// --------------------------------------------- Interface IFilePopupEntry ---------------------------------------------


  public JLabel getPopupEntryText(RearrangerSettings settings) {
    String s = commentText.trim();
    if (commentFillString != null && commentFillString.getFillString().length() > 0) {
      s = commentFillString.getExpandedCommentText(s);
    }
    return new JLabel(s);
  }

// ------------------------------------------- Interface IPopupTreeRangeEntry ------------------------------------------


  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode myNode = new RearrangerTreeNode(this, getName());
    parent.add(myNode);
    return myNode;
  }

// -------------------------------------------------- Interface IRule --------------------------------------------------


  @NotNull
  public RuleInstance createRuleInstance() {
    return CommentRuleInstanceFactory.buildCommentRuleInstance(this);
  }

  public boolean isMatch(@NotNull RangeEntry rangeEntry) {
    return false; // comment rules match no entries -- they're just placeholders to generate comments.
  }

  public boolean commentsMatchGlobalPattern(String pattern) {
    return commentText.matches(pattern);
  }

  public List<String> getOffendingPatterns(String pattern) {
    List<String> result = new ArrayList<String>(1);
    result.add(commentText);
    return result;
  }

  public void addCommentPatternsToList(List<String> list) {
    if (commentText != null && commentText.length() > 0) {
      String esc = RegexUtil.escape(commentText);
      String fsp = commentFillString.getFillStringPattern();
      esc = esc.replaceAll("%FS%", fsp);
      list.add(esc);
    }
  }

// --------------------------------------------------- OTHER METHODS ---------------------------------------------------

  public int getNPrecedingRulesToMatch() {
    return myNPrecedingRulesToMatch;
  }

  public final int getNSubsequentRulesToMatch() {
    return myNSubsequentRulesToMatch;
  }

// --------------------------------------------------- INNER CLASSES ---------------------------------------------------

  final class AnyAllPanel extends JPanel {
    final JCheckBox           anyAllCheckbox;
    final JComboBox           anyAllComboBox;
    final JLabel              anyAllLabel;
    final JFormattedTextField anyAllPrevNumber;
    final JLabel              rulesLabel;

    /**
     * Return a JPanel that allows user to specify checkbox (enable rule), Any/All combo box, "of the "
     * "preceding"/"subsequent", text field for integer >= 1, "rules"
     *
     * @param preceding true if handling "preceding" rules, otherwise "subsequent" rules
     */
    AnyAllPanel(final boolean preceding) {
      super(new GridBagLayout());
      final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
      constraints.lastRow();
      constraints.fill = GridBagConstraints.BOTH;
      anyAllCheckbox = new JCheckBox("items match");
      anyAllCheckbox.setSelected((emitCondition & (preceding ? 1 : 2)) > 0);
      anyAllComboBox = new JComboBox(new String[]{"any", "all"});
      anyAllComboBox.setSelectedIndex(preceding ? (myAllPrecedingRules ? 1 : 0)
                                                : (myAllSubsequentRules ? 1 : 0));
      Dimension d = anyAllComboBox.getPreferredSize();
      d.width += 3; // make room for "any", if "all" is narrower and is first selection.
      anyAllComboBox.setPreferredSize(d);
      anyAllLabel = new JLabel();
      anyAllLabel.setText("of the subsequent"); // calculate max size
      d = anyAllLabel.getPreferredSize();
      anyAllLabel.setText(preceding ? "of the preceding" : "of the subsequent");
      anyAllLabel.setMinimumSize(d);
      anyAllLabel.setPreferredSize(d);
      final NumberFormat integerInstance = NumberFormat.getIntegerInstance();
      integerInstance.setMaximumIntegerDigits(2);
      integerInstance.setMinimumIntegerDigits(1);
      anyAllPrevNumber = new JFormattedTextField(integerInstance);
      anyAllPrevNumber.setValue(new Integer("88"));
      d = anyAllPrevNumber.getPreferredSize();
      d.width += 3;
      anyAllPrevNumber.setPreferredSize(d);
      anyAllPrevNumber.setValue(preceding ? myNPrecedingRulesToMatch : myNSubsequentRulesToMatch);
      anyAllPrevNumber.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
      rulesLabel = new JLabel("rules");
      constraints.insets = new Insets(0, 10, 0, 0);
      add(anyAllCheckbox, constraints.nextCol());
      constraints.insets = new Insets(0, 0, 0, 0);
      add(anyAllComboBox, constraints.nextCol());
      constraints.insets = new Insets(0, 4, 0, 0);
      add(anyAllLabel, constraints.nextCol());
      constraints.insets = new Insets(0, 4, 0, 0);
      add(anyAllPrevNumber, constraints.nextCol());
      add(rulesLabel, constraints.weightedLastCol());
      anyAllComboBox.setEnabled(anyAllCheckbox.isSelected());
      anyAllLabel.setEnabled(anyAllCheckbox.isSelected());
      anyAllPrevNumber.setEnabled(anyAllCheckbox.isSelected());
      rulesLabel.setEnabled(anyAllCheckbox.isSelected());
      anyAllCheckbox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          if (anyAllCheckbox.isSelected()) {
            emitCondition |= (preceding ? 1 : 2);
          }
          else {
            emitCondition &= (preceding ? ~1 : ~2);
          }
          anyAllComboBox.setEnabled(anyAllCheckbox.isSelected());
          anyAllLabel.setEnabled(anyAllCheckbox.isSelected());
          anyAllPrevNumber.setEnabled(anyAllCheckbox.isSelected());
          rulesLabel.setEnabled(anyAllCheckbox.isSelected());
        }
      });
      anyAllComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final int s = anyAllComboBox.getSelectedIndex();
          if (preceding) {
            myAllPrecedingRules = (s == 1);
          }
          else {
            myAllSubsequentRules = (s == 1);
          }
        }
      });
      anyAllPrevNumber.addPropertyChangeListener("value", new PropertyChangeListener() {
        public void propertyChange(final PropertyChangeEvent evt) {
          int n = ((Number)anyAllPrevNumber.getValue()).intValue();
          if (n <= 0) {
            n = 1;
            anyAllPrevNumber.setValue(n);
          }
          if (preceding) {
            myNPrecedingRulesToMatch = n;
          }
          else {
            myNSubsequentRulesToMatch = n;
          }
        }
      });
    }

    public final void setEnabled(final boolean enable) {
      anyAllCheckbox.setEnabled(enable);
      anyAllComboBox.setEnabled(anyAllCheckbox.isSelected() && enable);
      anyAllLabel.setEnabled(anyAllCheckbox.isSelected() && enable);
      anyAllPrevNumber.setEnabled(anyAllCheckbox.isSelected() && enable);
      rulesLabel.setEnabled(anyAllCheckbox.isSelected() && enable);
    }
  }
}

