/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.application.options;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeStyleHtmlPanel extends CodeStyleAbstractPanel {

  private JTextField myKeepBlankLines;
  private JComboBox myWrapAttributes;
  private JCheckBox myAlignAttributes;
  private JCheckBox myKeepWhiteSpaces;

  private JPanel myPanel;
  private JPanel myPreviewPanel;

  private JCheckBox mySpacesAroundEquality;
  private JCheckBox mySpacesAroundTagName;
  private JCheckBox myAlignText;
  private JComboBox myTextWrapping;
  private JTextField myInsertNewLineTagNames;
  private JTextField myRemoveNewLineTagNames;
  private JTextField myDoNotAlignChildrenTagNames;
  private JTextField myKeepWhiteSpacesTagNames;
  private JTextField myTextElementsTagNames;
  private JTextField myDoNotAlignChildrenMinSize;
  private JCheckBox myShouldKeepBlankLines;

  public CodeStyleHtmlPanel(CodeStyleSettings settings) {
    super(settings);
    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    fillWrappingCombo(myWrapAttributes);
    fillWrappingCombo(myTextWrapping);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updatePreview();
      }
    };

    myKeepBlankLines.addActionListener(actionListener);
    myWrapAttributes.addActionListener(actionListener);
    myTextWrapping.addActionListener(actionListener);
    myKeepWhiteSpaces.addActionListener(actionListener);
    myAlignAttributes.addActionListener(actionListener);
    mySpacesAroundEquality.addActionListener(actionListener);
    mySpacesAroundTagName.addActionListener(actionListener);
    myAlignText.addActionListener(actionListener);

    final DocumentListener documentListener = new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void insertUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void removeUpdate(DocumentEvent e) {
        updatePreview();
      }
    };

    myKeepBlankLines.getDocument().addDocumentListener(documentListener);

    myInsertNewLineTagNames.getDocument().addDocumentListener(documentListener);
    myRemoveNewLineTagNames.getDocument().addDocumentListener(documentListener);
    myDoNotAlignChildrenTagNames.getDocument().addDocumentListener(documentListener);
    myDoNotAlignChildrenMinSize.getDocument().addDocumentListener(documentListener);
    myTextElementsTagNames.getDocument().addDocumentListener(documentListener);
    myKeepWhiteSpacesTagNames.getDocument().addDocumentListener(documentListener);
    myShouldKeepBlankLines.addActionListener(actionListener);

    myShouldKeepBlankLines.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myKeepBlankLines.setEnabled(myShouldKeepBlankLines.isSelected());
      }
    });
  }

  public void apply(CodeStyleSettings settings) {
    settings.HTML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.HTML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.HTML_TEXT_WRAP = ourWrappings[myTextWrapping.getSelectedIndex()];
    settings.HTML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.HTML_ALIGN_TEXT = myAlignText.isSelected();
    settings.HTML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.HTML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

    settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = myInsertNewLineTagNames.getText();
    settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = myRemoveNewLineTagNames.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF = myDoNotAlignChildrenTagNames.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE = getIntValue(myDoNotAlignChildrenMinSize);
    settings.HTML_TEXT_ELEMENTS = myTextElementsTagNames.getText();
    settings.HTML_KEEP_WHITESPACES_INSIDE = myKeepWhiteSpacesTagNames.getText();
    settings.HTML_KEEP_LINE_BREAKS = myShouldKeepBlankLines.isSelected();
  }

  private int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl() {
    myKeepBlankLines.setText(String.valueOf(mySettings.HTML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(mySettings.HTML_ATTRIBUTE_WRAP));
    myTextWrapping.setSelectedIndex(getIndexForWrapping(mySettings.HTML_TEXT_WRAP));
    myAlignAttributes.setSelected(mySettings.HTML_ALIGN_ATTRIBUTES);
    myAlignText.setSelected(mySettings.HTML_ALIGN_TEXT);
    myKeepWhiteSpaces.setSelected(mySettings.HTML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(mySettings.HTML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(mySettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
    myShouldKeepBlankLines.setSelected(mySettings.HTML_KEEP_LINE_BREAKS);

    myInsertNewLineTagNames.setText(mySettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
    myRemoveNewLineTagNames.setText(mySettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
    myDoNotAlignChildrenTagNames.setText(mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF);
    myDoNotAlignChildrenMinSize.setText(String.valueOf(mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE));
    myTextElementsTagNames.setText(mySettings.HTML_TEXT_ELEMENTS);
    myKeepWhiteSpacesTagNames.setText(mySettings.HTML_KEEP_WHITESPACES_INSIDE);

    myKeepBlankLines.setEnabled(myShouldKeepBlankLines.isSelected());
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.HTML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.HTML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }

    if (settings.HTML_TEXT_WRAP != ourWrappings[myTextWrapping.getSelectedIndex()]) {
      return true;
    }

    if (settings.HTML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }

    if (settings.HTML_ALIGN_TEXT != myAlignText.isSelected()) {
      return true;
    }

    if (settings.HTML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.HTML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()){
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE, myInsertNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE, myRemoveNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_DO_NOT_ALIGN_CHILDREN_OF, myDoNotAlignChildrenTagNames.getText().trim())){
      return true;
    }

    if (settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE != getIntValue(myDoNotAlignChildrenMinSize)){
      return true;
    }

    if (!Comparing.equal(settings.HTML_TEXT_ELEMENTS, myTextElementsTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_KEEP_WHITESPACES_INSIDE, myKeepWhiteSpacesTagNames.getText().trim())){
      return true;
    }

    if (myShouldKeepBlankLines.isSelected() != mySettings.HTML_KEEP_LINE_BREAKS) {
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" +
           "    \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
           "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">\n" +
           "<head>\n" +
           "<title>ReSharper: The Most Intelligent Add-In To VisualStudio .NET</title>\n" +
           "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" />\n" +
           "<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../css/main.css\"/>\n" +
           "<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../css/resharper.css\"/>\n" +
           "<link rel=\"stylesheet\" type=\"text/css\" media=\"print\" href=\"../css/print.css\"/>\n" +
           "<link rel=\"Shortcut Icon\" href=\"../favicon.ico\" type=\"image/x-icon\"/>\n" +
           "\n" +
           "</head>\n" +
           "\n" +
           "<body class=\"resharperbg\">\n" +
           "<div id=\"container\">\n" +
           "\n" +
           "   <div id=\"top\">\n" +
           "       <div id=\"logo\"><a href=\"../index.html\"><img src=\"../img/logo_bw.gif\" width=\"124\" height=\"44\" alt=\"JetBrains home\"/></a></div>\n" +
           "\n" +
           "        <div id=\"nav\">\n" +
           "            <ul id=\"topnav\">\n" +
           "                <li class=\"home\"><a href=\"../index.html\">Home</a></li>\n" +
           "                <li class=\"act\"><a href=\"../products.html\">Products</a></li>\n" +
           "                <li><a href=\"../support\">Support</a></li>\n" +
           "\t\t\t\t<li><a href=\"../devnet\">Developers</a></li>\n" +
           "                <li><a href=\"../company\">Company</a></li>\n" +
           "            </ul>\n" +
           "        </div>\n" +
           "    </div>\n" +
           "\n" +
           "    <div id=\"bannerre\"><h2>ReSharper</h2></div>\n" +
           "   <div id=\"subnavresharper\">\n" +
           "    <ul id=\"subnavre\">\n" +
           "\t    <li class=\"active\">ReSharper home</li>\n" +
           "\t    <li><a href=\"features\">Features</a></li>\n" +
           "\t    <li><a href=\"documentation\">Documentation</a></li>\n" +
           "        <li><a href=\"licensing\">Licensing Policy</a></li>\n" +
           "\t    <li><a href=\"download\">Download</a></li>\n" +
           "\t    <li><a href=\"buy\">Buy</a></li>\n" +
           "\t</ul>\n" +
           "    </div>\n" +
           "\n" +
           "    <div class=\"clear\"></div>\n" +
           "    <div id=\"bc\"><a href=\"../index.html\">Home</a><img src=\"../img/bc.gif\" alt=\">\"/><a href=\"../products.html\">Products</a><img src=\"../img/bc.gif\" alt=\">\"/>ReSharper</div>\n" +
           "    <div id=\"subcontent\">\n" +
           "        <a class=\"nol\" href=\"buy/specials\"><img src=\"../img/banners/rsb4.gif\" alt=\"\"/></a>\n" +
           "    <br/>\n" +
           "    \t<div id=\"whatsnew_res\"><h4>License Lottery</h4></div>\n" +
           "            <div id=\"wnew\">\n" +
           "            <p><b>Participate and win</b> <br/>\n" +
           "            <a href=\"survey.html\">free ReSharper license</a>\n" +
           "            </p>\n" +
           "            </div>\n" +
           "\n" +
           "        <br/>\n" +
           "\n" +
           "        <div id=\"greybox\">\n" +
           "      \t\t<h4>Keep Me Informed</h4>\n" +
           "      \t\t<p>Subscribe to JetBrains product news and documentation updates</p>\n" +
           "\n" +
           "      \t\t<form name=\"subscribe\" action=\"http://www.jetbrains.com/forms/subscribe\" method=\"post\">\n" +
           "                        name:\n" +
           "        \t\t<div>\n" +
           "        \t\t\t<input size=\"18\" class=\"text\" name=\"name\"/>\n" +
           "        \t\t</div>\n" +
           "        \t\te-mail:\n" +
           "        \t\t<div>\n" +
           "        \t\t\t<input size=\"18\" class=\"text\" name=\"email\"/>\n" +
           "        \t\t</div>\n" +
           "        \t\t<a href=\"javascript: document.forms.subscribe.submit();\"><img src=\"../img/button_subscribe.gif\" width=\"140\" height=\"26\" alt=\"subscribe\"/></a>\n" +
           "      \t\t</form>\n" +
           "    \t</div>\n" +
           "    \t<div id=\"quote\">\n" +
           "                <p class=\"text\">&quot;Resharper has been making its way through my team like a virus. I've been using it for a couple of months now, and I think I can say I'm sold.&quot;</p>\n" +
           "                <p class=\"author\"><a target=\"\" href=\"http://hyperthink.net/blog/PermaLink,guid,c84fb408-a764-48f4-870e-498191031c89.aspx\">Steve Maine</a>, <br/>Solutions developer,<br/> Avanade</p>\n" +
           "        </div>\n" +
           "        <p class=\"spacer\"></p>\n" +
           "\n" +
           "        <div id=\"quote\">\n" +
           "                <p class=\"text\">&quot;Incidentally, if you want excellent refactoring support for C# in Visual Studio.Net\n" +
           "                today, check out JetBrains' super ReSharper tool. It supports renaming, extracting methods, turning fields\n" +
           "                into properties, encapsulating code with if or while constructs, templates and much more!&quot;</p>\n" +
           "                <p class=\"author\"><a target=\"\" href=\"http://dotnetjunkies.net/WebLog/roydictus/archive/2004/11/16/32279.aspx\">Roy Dictus</a>, <br/>Architect and Trainer,<br/> U2U, Belgium's leading .Net Competence Center</p>\n" +
           "        </div>\n" +
           "        <p class=\"spacer\"></p>\n" +
           "\n" +
           "        <div id=\"quote\">\n" +
           "                <p class=\"text\">&quot;If you are a C# developer you simply owe it to yourself to get this tool. It has\n" +
           "                dramatically made my life better (SERIOUSLY!). I put it up there with amazing tools…&quot;</p>\n" +
           "                <p class=\"author\"><a target=\"\" href=\"http://agiledamon.blogspot.com/2004/09/my-latest-net-developers-best-friend.html\">Damon Wilder Carr</a>, <br/>Chief Technologist and CEO,<br/> Agilerfactor</p>\n" +
           "        </div>\n" +
           "        <p class=\"spacer\"></p>\n" +
           "\n" +
           "         <div>\n" +
           "             <a class=\"nol\" title=\"opens in a new window\" href=\"http://www.adtmag.com/article.asp?id=9843\" target=\"blank\">\n" +
           "             <img src=\"../img/logos/logo_adt.gif\" alt=\"ADT Magazine reviews ReSharper\" width=\"100\" height=\"32\"/></a>\n" +
           "             <p class=\"aw\"><a title=\"opens in a new window\" href=\"http://www.adtmag.com/article.asp?id=9843\" target=\"blank\">ADT Magazine reviews ReSharper 1.0.1</a></p>\n" +
           "             <br/>\n" +
           "             <a class=\"nol\" title=\"opens in a new window\" href=\"http://www.sdtimes.com/news/101/story11.htm\" target=\"blank\"><img src=\"../img/logos/logo_sd_times.gif\" width=\"90\" height=\"33\" alt=\"SD Times reviews ReSharper\"/></a>\n" +
           "             <p class=\"aw\"><a title=\"opens in a new window\" href=\"http://www.sdtimes.com/news/101/story11.htm\" target=\"blank\">&quot;Another IDEA: JetBrains to Launch C#, RAD Tools&quot;</a></p>\n" +
           "         </div>\n" +
           "\n" +
           "    </div>\n" +
           "\n" +
           "    <div id=\"content\">\n" +
           "        <h1>The Most Intelligent Add-In To VisualStudio.NET</h1>\n" +
           "        <div id=\"product\">\n" +
           "            <div id=\"firstcol\">\n" +
           "            <p style=\"margin-bottom:3.5em; margin-top:0.5em;\"><img src=\"../img/resharper1.gif\" width=\"266\" height=\"45\" alt=\"ReSharper 1.0\"/></p>\n" +
           "            </div>\n" +
           "            <div id=\"secondcol\">\n" +
           "                <p class=\"moredownload\"><a href=\"download/index.html\" title=\"Download IntelliJ IDEA Java IDE\"><b>download</b></a></p>\n" +
           "                <p class=\"morebuy\"><a href=\"buy/index.html\" title=\"Buy IntelliJ IDEA Java IDE\"><b>buy</b></a></p>\n" +
           "            </div>\n" +
           "        </div>\n" +
           "        <div class=\"clear\"></div>\n" +
           "        <hr class=\"hide\"/>\n" +
           "        <div id=\"overview\">\n" +
           "\t\t\t<p class=\"bigtext\">ReSharper was created with the single purpose in mind: to increase the productivity of\n" +
           "            C# developers. It comes equipped with a rich set of features, such as intelligent coding assistance, on-the-fly\n" +
           "            error highlighting and quick error correction, unmatched support for code refactoring, and a whole lot more.\n" +
           "            ReSharper's tight integration with Visual Studio .NET provides quick and easy access to all of its advanced\n" +
           "            features right from the IDE.</p>\n" +
           "            <p class=\"more\"><a class=\"nol\" href=\"documentation/1.0_DataSheet.pdf\" target=\"blank\"><img src=\"../img/icon_pdf.gif\" width=\"16\" height=\"16\" alt=\"PDF\"/></a>&nbsp; <a href=\"documentation/1.0_DataSheet.pdf\" target=\"blank\">ReSharper Data Sheet</a> <span class=\"number\">(111 Kb)</span></p>\n" +
           "            <p class=\"more\"><a class=\"nol\" href=\"documentation/1.0_ReferenceCard.pdf\" target=\"blank\"><img src=\"../img/icon_pdf.gif\" width=\"16\" height=\"16\" alt=\"PDF\"/></a>&nbsp; <a href=\"documentation/1.0_ReferenceCard.pdf\" target=\"blank\">ReSharper 1.0 Default Keymap</a> <span class=\"number\">(44 Kb)</span></p>\n" +
           "            <br/>\n" +
           "\n" +
           "            <h3>ReSharper Key Features</h3>\n" +
           "            <br/>\n" +
           "            <div class=\"feature\">\n" +
           "                <h3>Error Highlighting</h3>\n" +
           "                <img src=\"features/previews/errors.gif\" alt=\"Error Highlighting\"/>\n" +
           "                <p>One of the most powerful and helpful features in ReSharper is its ability to quickly detect and\n" +
           "                highlight errors in code, without the need to compile it. ReSharper automatically analyzes your code while\n" +
           "                you work and will highlight a variety of possible syntax or logical errors. It can also detect and emphasize\n" +
           "                statements or constructs that you should be warned about (e.g., unused or uninitialized variables).</p>\n" +
           "                <p><a href=\"features/highlighting.html\">More about this feature…</a></p>\n" +
           "            </div>\n" +
           "            <div class=\"feature\">\n" +
           "                <h3>Error Quick-Fixes</h3>\n" +
           "                <img src=\"features/previews/undefinedMethod.gif\" alt=\"Error Quick Fixes\"/>\n" +
           "                <p>Not only does ReSharper highlight errors, but it also provides useful quick-fixes that enable you to\n" +
           "                instantly correct the code. When you place the caret on a highlighted error, if quick-fixes for this error\n" +
           "                are available, a small light-bulb icon pops up at the relevant line. Click it or use the Alt+Enter shortcut,\n" +
           "                to see the list of available quick-fixes. Select the desired quick-fix and press Enter to apply it. </p>\n" +
           "                <p><a href=\"features/quickFixes.html\">More about this feature…</a></p>\n" +
           "            </div>\n" +
           "            <div class=\"feature\">\n" +
           "                <h3>Refactoring Support</h3>\n" +
           "                <p>Refactoring can significantly improve your code design and efficiency. ReSharper's automated refactoring\n" +
           "                support takes care of code consistency and compilability after even the most dramatic modifications.</p>\n" +
           "                <p>ReSharper supports the following types of refactoring:</p>\n" +
           "                <ul class=\"starlist\">\n" +
           "                    <li>Rename symbol with reference correction</li>\n" +
           "                    <li>Move type to another namespace with reference correction</li>\n" +
           "                    <li>Move type declaration to a separate file</li>\n" +
           "                    <li>Change method signature (add/remove/reorder parameters, change parameter type or return type)</li>\n" +
           "                    <li>Extract method</li>\n" +
           "                    <li>Introduce variable</li>\n" +
           "                    <li>Inline variable</li>\n" +
           "                    <li>Convert method to property</li>\n" +
           "                    <li>Convert property to method(s)</li>\n" +
           "                </ul>\n" +
           "                <p><a href=\"features/refactoring.html\">More about this feature…</a></p>\n" +
           "            </div>\n" +
           "            <div class=\"feature\">\n" +
           "                <h3>Live Templates</h3>\n" +
           "                <img src=\"features/previews/itar.gif\" alt=\"Live Templates\"/>\n" +
           "                <p>Live templates represent predefined code fragments that can &quot;interact&quot; with the developer\n" +
           "                when inserted. You can use them to insert common or custom code constructs into a source code file quickly,\n" +
           "                efficiently, and accurately.</p>\n" +
           "                <p>Just type a particular template's abbreviation, then press the Tab key to expand it into your source file.</p>\n" +
           "                <p><a href=\"features/liveTemplates.html\">More about this feature…</a></p>\n" +
           "            </div>\n" +
           "\n" +
           "            <p>To get the full story on ReSharper&#146;s feature set, please visit the <a href=\"features/index.html\">Features</a> page.</p>\n" +
           "\n" +
           "            <h3>Why ReSharper</h3>\n" +
           "            <p>ReSharper makes C# development a real pleasure. It decreases the time you spend on routine,  repetitive\n" +
           "            handwork, giving you more time to focus on the task at hand. Its robust set of features for automatic error-checking\n" +
           "            and code correction cuts development time and increases  your  efficiency.  You'll find that ReSharper quickly\n" +
           "            pays back it's cost in increased developer productivity and improved code quality.</p>\n" +
           "            <p>The wait is over… ReSharper is here and now C# developers can experience what we mean when we say\n" +
           "            \"Develop with pleasure!\" <a href=\"download\">Download a copy today</a>!</p>\n" +
           "        </div>\n" +
           "\n" +
           "    </div>\n" +
           "</div>\n" +
           "    <hr class=\"hide\"/>\n" +
           "    <div class=\"clear\"></div>\n" +
           "<div id=\"fcontainer\">\n" +
           "    <div id=\"footer\">\n" +
           "    <div id=\"copy\">Copyright &copy; 2000-2004 JetBrains. All rights reserved</div>\n" +
           "    <div id=\"mail\">Comments? Write to <a href=\"mailto:webmaster@jetbrains.com\">webmaster@jetbrains.com</a></div>\n" +
           "    </div>\n" +
           "</div>\n" +
           "\n" +
           "</body>\n" +
           "</html>";
  }

  protected FileType getFileType() {
    return StdFileTypes.HTML;
  }
}
