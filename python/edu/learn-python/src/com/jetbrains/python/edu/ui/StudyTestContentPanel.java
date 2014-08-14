package com.jetbrains.python.edu.ui;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.edu.course.UserTest;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;

public class StudyTestContentPanel extends JPanel {
  public static final Dimension PREFERRED_SIZE = new Dimension(300, 200);
  private static final Font HEADER_FONT = new Font("Arial", Font.BOLD, 16);
  private final JTextArea myInputArea = new JTextArea();
  private final JTextArea myOutputArea = new JTextArea();
  public StudyTestContentPanel(UserTest userTest) {
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    initContentLabel("input", myInputArea);
    myInputArea.getDocument().addDocumentListener(new BufferUpdater(userTest.getInputBuffer()));
    myOutputArea.getDocument().addDocumentListener(new BufferUpdater(userTest.getOutputBuffer()));
    initContentLabel("output", myOutputArea);
    setEditable(userTest.isEditable());
  }

  private void initContentLabel(final String headerText, @NotNull final JTextArea contentArea) {
    JLabel headerLabel = new JLabel(headerText);
    headerLabel.setFont(HEADER_FONT);
    this.add(headerLabel);
    this.add(new JSeparator(SwingConstants.HORIZONTAL));
    JScrollPane scroll = new JBScrollPane(contentArea);
    scroll.setPreferredSize(PREFERRED_SIZE);
    this.add(scroll);
  }

  private void setEditable(boolean isEditable) {
    myInputArea.setEditable(isEditable);
    myOutputArea.setEditable(isEditable);
  }
  public void addInputContent(final String content) {
    myInputArea.setText(content);
  }

  public  void addOutputContent(final String content) {
    myOutputArea.setText(content);
  }

  private class BufferUpdater extends DocumentAdapter {
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
      catch (BadLocationException e1) {
        e1.printStackTrace();
      }
    }
  }
}
