package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;

public class StudyTestContentPanel extends JPanel {
  private static final Dimension ourPreferredSize = new Dimension(300, 200);
  private static final Font ourHeaderFont = new Font("Arial", Font.BOLD, 16);
  private final JTextArea myInputArea = new JTextArea();
  private final JTextArea myOutputArea = new JTextArea();
  private static final Logger LOG = Logger.getInstance(StudyTestContentPanel.class.getName());

  public StudyTestContentPanel(UserTest userTest) {
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    initContentLabel("input", myInputArea);
    myInputArea.getDocument().addDocumentListener(new BufferUpdater(userTest.getInputBuffer()));
    myOutputArea.getDocument().addDocumentListener(new BufferUpdater(userTest.getOutputBuffer()));
    initContentLabel("output", myOutputArea);
    setEditable(userTest.isEditable());
  }

  private void initContentLabel(@NotNull final String headerText, @NotNull final JTextArea contentArea) {
    JLabel headerLabel = new JLabel(headerText);
    headerLabel.setFont(ourHeaderFont);
    this.add(headerLabel);
    this.add(new JSeparator(SwingConstants.HORIZONTAL));
    JScrollPane scroll = new JBScrollPane(contentArea);
    scroll.setPreferredSize(ourPreferredSize);
    this.add(scroll);
  }

  private void setEditable(boolean isEditable) {
    myInputArea.setEditable(isEditable);
    myOutputArea.setEditable(isEditable);
  }

  public void addInputContent(final String content) {
    myInputArea.setText(content);
  }

  public void addOutputContent(final String content) {
    myOutputArea.setText(content);
  }

  private static class BufferUpdater extends DocumentAdapter {
    private final StringBuilder myBuffer;

    private BufferUpdater(StringBuilder buffer) {
      myBuffer = buffer;
    }

    @Override
    protected void textChanged(DocumentEvent e) {
      myBuffer.delete(0, myBuffer.length());
      try {
        myBuffer.append(e.getDocument().getText(0, e.getDocument().getLength()));
      }
      catch (BadLocationException exception) {
        LOG.warn(exception);
      }
    }
  }
}
