package com.intellij.internal.encodings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class EncodingViewer extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.encodings.EncodingViewer");
  private JPanel myPanel;
  private JTextField myText;
  private JComboBox myEncoding;
  private JButton myLoadFile;
  private byte[] myBytes;

  public EncodingViewer() {
    super(false);
    initEncodings();
    myLoadFile.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myPanel, descriptor);
        if (files.length != 0) {
          loadFrom(files[0]);
        }
      }
    });
    init();
  }

  protected String getDimensionServiceKey() {
    return "EncodingViewer";
  }

  private void loadFrom(VirtualFile virtualFile) {
    try {
      myBytes = LocalFileSystem.getInstance().physicalContentsToByteArray(virtualFile);
    } catch (IOException e) {
      LOG.error(e);
      return;
    }
    refreshText();
  }

  private void refreshText() {
    String selectedCharset = getSelectedCharset();
    if (myBytes == null || selectedCharset == null) return;
    try {
      myText.setText(new String(myBytes, selectedCharset));
    } catch (UnsupportedEncodingException e) {
      LOG.error(e);
    }
  }

  private String getSelectedCharset() {
    return ((Charset) myEncoding.getSelectedItem()).name();
  }

  private void initEncodings() {
    Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();
    myEncoding.setModel(new DefaultComboBoxModel(availableCharsets));
    int defaultIndex = Arrays.asList(availableCharsets).indexOf(CharsetToolkit.getDefaultSystemCharset());
    myEncoding.setSelectedIndex(defaultIndex);
    myEncoding.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) return;
        refreshText();
      }
    });
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

}
