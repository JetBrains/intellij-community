package com.intellij.ide.fileTemplates.impl;

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.lexer.CompositeLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerHighlighter;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.Vector;

/*
 * @author: MYakovlev
 * Date: Jul 26, 2002
 * Time: 12:46:00 PM
 */

public class FileTemplateConfigurable implements Configurable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable");

  private JPanel myMainPanel;
  private FileTemplate myTemplate;
  private Editor myTemplateEditor;
  private JTextField myNameField;
  private JTextField myExtensionField;
  private JCheckBox myAdjustBox;
  private JPanel myTopPanel;
  private Splitter mySplitter;
  private JEditorPane myDescriptionComponent;
  private boolean myModified = false;
  private Vector myChangeListeners = new Vector();
  private VirtualFile myDefaultDescription;

  public FileTemplate getTemplate() {
    return myTemplate;
  }

  public void setTemplate(FileTemplate template, VirtualFile defaultDescription) {
    myDefaultDescription = defaultDescription;
    myTemplate = template;
    reset();
    myNameField.selectAll();
    myExtensionField.selectAll();
  }

  public void setShowInternalMessage(String message) {
    if (message == null) {
      myTopPanel.removeAll();
      myTopPanel.add(new JLabel("Name:"),
                     new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                            new Insets(0, 0, 0, 2), 0, 0));
      myTopPanel.add(myNameField,
                     new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                            GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 2), 0, 0));
      myTopPanel.add(new JLabel("Extension:"),
                     new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                            new Insets(0, 2, 0, 2), 0, 0));
      myTopPanel.add(myExtensionField,
                     new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                                            GridBagConstraints.HORIZONTAL, new Insets(0, 2, 0, 0), 0, 0));
      myExtensionField.setColumns(7);
    }
    else {
      myTopPanel.removeAll();
      myTopPanel.add(new JLabel(message),
                     new GridBagConstraints(0, 0, 4, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      myTopPanel.add(Box.createVerticalStrut(myNameField.getPreferredSize().height),
                     new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                            GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    }
    myMainPanel.revalidate();
    myTopPanel.repaint();
  }

  public void setShowAdjustCheckBox(boolean show) {
    myAdjustBox.setEnabled(show);
  }

  public String getDisplayName() {
    return "File Templates";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    myMainPanel = new JPanel(new GridBagLayout());
    myTemplateEditor = createEditor();
    myNameField = new JTextField();
    myExtensionField = new JTextField();
    mySplitter = new Splitter(true, 0.66f);

    myDescriptionComponent = new JEditorPane("text/html", "<html></html>");
    myDescriptionComponent.setEditable(false);
//    myDescriptionComponent.setMargin(new Insets(2, 2, 2, 2));

//    myDescriptionComponent = new JLabel();
//    myDescriptionComponent.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
//    myDescriptionComponent.setVerticalAlignment(SwingConstants.TOP);

    myAdjustBox = new JCheckBox("Reformat according to style");
    myTopPanel = new JPanel(new GridBagLayout());

    JPanel secondPanel = new JPanel(new GridBagLayout());
    secondPanel.add(new JLabel("Description:"),
                    new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                           new Insets(0, 0, 2, 0), 0, 0));
    secondPanel.add(new JScrollPane(myDescriptionComponent),
                    new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(2, 0, 0, 0), 0, 0));

    myMainPanel.add(myTopPanel,
                    new GridBagConstraints(0, 0, 4, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                                           GridBagConstraints.HORIZONTAL, new Insets(0, 0, 2, 0), 0, 0));
    myMainPanel.add(myAdjustBox,
                    new GridBagConstraints(0, 1, 4, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                           GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));
    myMainPanel.add(mySplitter,
                    new GridBagConstraints(0, 2, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                           new Insets(2, 0, 0, 0), 0, 0));
    mySplitter.setFirstComponent(myTemplateEditor.getComponent());
    mySplitter.setSecondComponent(secondPanel);
    setShowInternalMessage(null);
    myTemplateEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        onTextChanged();
      }
    });
    myNameField.addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        onNameChanged();
      }
    });
    myExtensionField.addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        onNameChanged();
      }
    });
    return myMainPanel;
  }

  private static Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document doc = editorFactory.createDocument("");
    Editor editor = editorFactory.createEditor(doc);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(3);
    editorSettings.setAdditionalLinesCount(3);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    return editor;
  }

  private void onTextChanged() {
    myModified = true;
  }

  public String getNameValue() {
    return myNameField.getText();
  }

  public String getExtensionValue() {
    return myExtensionField.getText();
  }

  private void onNameChanged() {
    ChangeEvent event = new ChangeEvent(this);
    for (int i = 0; i < myChangeListeners.size(); i++) {
      ChangeListener changeListener = (ChangeListener)myChangeListeners.elementAt(i);
      changeListener.stateChanged(event);
    }
  }

  public void addChangeListener(ChangeListener listener) {
    if (!myChangeListeners.contains(listener)) {
      myChangeListeners.add(listener);
    }
  }

  public void removeChangeListener(ChangeListener listener) {
    myChangeListeners.remove(listener);
  }

  public boolean isModified() {
    if (myModified) {
      return true;
    }
    String name = (myTemplate == null) ? "" : myTemplate.getName();
    String extension = (myTemplate == null) ? "" : myTemplate.getExtension();
    if (!Comparing.equal(name, myNameField.getText())) {
      return true;
    }
    if (!Comparing.equal(extension, myExtensionField.getText())) {
      return true;
    }
    if (myTemplate != null) {
      if (myTemplate.isAdjust() != myAdjustBox.isSelected()) {
        return true;
      }
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    if (myTemplate != null) {
      myTemplate.setText(myTemplateEditor.getDocument().getText());
      String name = myNameField.getText();
      String extension = myExtensionField.getText();
      int lastDotIndex = extension.lastIndexOf(".");
      if (lastDotIndex >= 0) {
        name = name + extension.substring(0, lastDotIndex + 1);
        extension = extension.substring(lastDotIndex + 1);
      }
      myTemplate.setName(name);
      myTemplate.setExtension(extension);
      myTemplate.setAdjust(myAdjustBox.isSelected());
    }
    myModified = false;
  }

  public void reset() {
    final String text = (myTemplate == null) ? "" : myTemplate.getText();
    String name = (myTemplate == null) ? "" : myTemplate.getName();
    String extension = (myTemplate == null) ? "" : myTemplate.getExtension();
    String description = (myTemplate == null) ? "" : myTemplate.getDescription();
    if (description == null) {
      description = "";
    }
    if ((description.length() == 0) && (myDefaultDescription != null)) {
      try {
        description = new String(myDefaultDescription.contentsToCharArray());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    boolean adjust = (myTemplate == null) ? false : myTemplate.isAdjust();
    setHighlighter();
    myNameField.setText(name);
    myExtensionField.setText(extension);
    myAdjustBox.setSelected(adjust);
    String desc = description.length() > 0 ? description : "<html></html>";

    // [myakovlev] do not delete these stupid lines! Or you get Exception!
    myDescriptionComponent.setContentType("text/plain");
    myDescriptionComponent.setEditable(true);
    myDescriptionComponent.setText(desc);
    myDescriptionComponent.setContentType("text/html");
    myDescriptionComponent.setText(desc);
    myDescriptionComponent.setCaretPosition(0);
    myDescriptionComponent.setEditable(false);

    CommandProcessor.getInstance().executeCommand(null, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            myTemplateEditor.getDocument().replaceString(0, myTemplateEditor.getDocument().getTextLength(), text);
          }
        });
      }
    },
                                                  "",
                                                  null);
    UndoManager.getGlobalInstance().clearUndoRedoQueue(TextEditorProvider.getInstance().getTextEditor(myTemplateEditor));
    myNameField.setEditable((myTemplate != null) && (!myTemplate.isDefault()));
    myExtensionField.setEditable((myTemplate != null) && (!myTemplate.isDefault()));
    myModified = false;
  }

  public void disposeUIResources() {
    myMainPanel = null;
    if (myTemplateEditor != null) {
      EditorFactory.getInstance().releaseEditor(myTemplateEditor);
      myTemplateEditor = null;
    }
  }

  private void setHighlighter() {
    FileType fileType;
    if (myTemplate != null) {
      String extension = myTemplate.getExtension();
      fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
    }
    else {
      fileType = StdFileTypes.PLAIN_TEXT;
    }

    FileHighlighter originalHighlighter = fileType.getHighlighter(null);
    LexerHighlighter highlighter = new LexerHighlighter(new TemplateHighlighter(originalHighlighter), EditorColorsManager.getInstance().getGlobalScheme());
    ((EditorEx)myTemplateEditor).setHighlighter(highlighter);
    ((EditorEx)myTemplateEditor).repaint(0, myTemplateEditor.getDocument().getTextLength());
  }

  private final static TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{FileTemplateTokenType.TEXT});

  private static class TemplateHighlighter extends FileHighlighterBase {
    private Lexer myLexer;
    private FileHighlighter myOriginalHighlighter;

    public TemplateHighlighter(FileHighlighter original) {
      myOriginalHighlighter = original;
      Lexer originalLexer = original.getHighlightingLexer();
      Lexer templateLexer = new FileTemplateTextLexer();
      templateLexer = new MergingLexerAdapter(templateLexer, TOKENS_TO_MERGE);

      myLexer = new CompositeLexer(originalLexer, templateLexer) {
        protected IElementType getCompositeTokenType(IElementType type1, IElementType type2) {
          if (type2 == FileTemplateTokenType.MACRO || type2 == FileTemplateTokenType.DIRECTIVE) {
            return type2;
          }
          else {
            return type1;
          }
        }
      };
    }

    public Lexer getHighlightingLexer() {
      return myLexer;
    }

    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      if (tokenType == FileTemplateTokenType.MACRO) {
        return pack(myOriginalHighlighter.getTokenHighlights(tokenType), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
      }
      else if (tokenType == FileTemplateTokenType.DIRECTIVE) {
        return pack(myOriginalHighlighter.getTokenHighlights(tokenType), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
      }

      return myOriginalHighlighter.getTokenHighlights(tokenType);
    }
  }


  public void focusToNameField() {
    myNameField.selectAll();
    myNameField.requestFocus();
  }

  public void focusToExtensionField() {
    myExtensionField.selectAll();
    myExtensionField.requestFocus();
  }
}
