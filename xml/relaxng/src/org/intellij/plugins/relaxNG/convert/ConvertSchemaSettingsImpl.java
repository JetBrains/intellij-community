/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.convert;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

public class ConvertSchemaSettingsImpl implements ConvertSchemaSettings {
  static final String OUTPUT_TYPE = "output-type";
  static final String OUTPUT_PATH = "output-path";

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Project myProject;
  private final SchemaType myInputType;

  private Map<String, ?> myInputOptions = Collections.emptyMap();
  private Map<String, ?> myOutputOptions = Collections.emptyMap();

  private JPanel myRoot;

  private JRadioButton myOutputRng;
  private JRadioButton myOutputRnc;
  private JRadioButton myOutputXsd;
  private JRadioButton myOutputDtd;

  private ComboBox<String> myEncoding;

  private JTextField myIndent;
  private JTextField myLineLength;

  private TextFieldWithBrowseButton myOutputDestination;

  public ConvertSchemaSettingsImpl(Project project, @NotNull SchemaType inputType, VirtualFile firstFile) {
    myProject = project;
    myInputType = inputType;

    final FileType type;
    switch (inputType) {
      case RNG -> {
        myOutputRng.setVisible(false);
        myOutputXsd.setSelected(true);
        type = XmlFileType.INSTANCE;
      }
      case RNC -> {
        myOutputRnc.setVisible(false);
        myOutputRng.setSelected(true);
        type = RncFileType.getInstance();
      }
      case XSD -> {
        myOutputXsd.setVisible(false);
        myOutputRng.setSelected(true);
        type = XmlFileType.INSTANCE;
      }
      case DTD -> {
        myOutputDtd.setVisible(false);
        myOutputRng.setSelected(true);
        type = DTDFileType.INSTANCE;
      }
      case XML -> {
        myOutputRng.setSelected(true);
        type = XmlFileType.INSTANCE;
      }
      default -> {
        assert false;
        type = null;
      }
    }

    final Charset[] charsets = CharsetToolkit.getAvailableCharsets();
    final List<String> suggestions = new ArrayList<>(charsets.length);
    for (Charset charset : charsets) {
      if (charset.canEncode()) {
        String name = charset.name();
        suggestions.add(name);
      }
    }

    myEncoding.setModel(new DefaultComboBoxModel<>(ArrayUtil.toStringArray(suggestions)));
    final Charset charset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    myEncoding.setSelectedItem(charset.name()); //NON-NLS

    final CodeStyleSettings styleSettings = CodeStyle.getSettings(project);
    final int indent = styleSettings.getIndentSize(type);
    myIndent.setText(String.valueOf(indent));

    myLineLength.setText(String.valueOf(styleSettings.getDefaultRightMargin()));
    final SchemaType outputType = getOutputType();
    myLineLength.setEnabled(outputType == SchemaType.DTD || outputType == SchemaType.RNC);

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(firstFile);
    descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, module);

    myOutputDestination.addBrowseFolderListener(RelaxngBundle.message("relaxng.convert-schema.settings.destination.title"),
                                                RelaxngBundle.message("relaxng.convert-schema.settings.destination.message"), project, descriptor);

    final JTextField tf = myOutputDestination.getTextField();
    tf.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myPropertyChangeSupport.firePropertyChange(OUTPUT_PATH, null, getOutputDestination());
      }
    });
    tf.setText(firstFile.getParent().getPath().replace('/', File.separatorChar));

    final ItemListener listener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          final SchemaType type = getOutputType();
          myPropertyChangeSupport.firePropertyChange(OUTPUT_TYPE, null, type);
          myLineLength.setEnabled(type == SchemaType.DTD || type == SchemaType.RNC);
        }
      }
    };
    myOutputRng.addItemListener(listener);
    myOutputRnc.addItemListener(listener);
    myOutputXsd.addItemListener(listener);
    myOutputDtd.addItemListener(listener);

    if (inputType == SchemaType.DTD) {
      myInputOptions = AdvancedDtdOptions.prepareNamespaceMap(project, firstFile);
    }
  }

  @Override
  public @NotNull SchemaType getOutputType() {
    if (myOutputRng.isSelected()) {
      return SchemaType.RNG;
    } else if (myOutputRnc.isSelected()) {
      return SchemaType.RNC;
    } else if (myOutputXsd.isSelected()) {
      return SchemaType.XSD;
    } else {
      assert myOutputDtd.isSelected();
      return SchemaType.DTD;
    }
  }

  @Override
  public String getOutputEncoding() {
    return (String)myEncoding.getSelectedItem();
  }

  @Override
  public int getIndent() {
    return parseInt(myIndent.getText().trim());
  }

  private static int parseInt(String s) {
    try {
      return s.length() > 0 ? Integer.parseInt(s) : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @Override
  public int getLineLength() {
    return parseInt(myLineLength.getText());
  }

  @Override
  public String getOutputDestination() {
    return myOutputDestination.getText();
  }

  @Override
  public void addAdvancedSettings(List<? super String> inputParams, List<? super String> outputParams) {
    setParams(myInputOptions, inputParams);

    if (getOutputType() == SchemaType.XSD) {
      setParams(myOutputOptions, outputParams);
    }
  }

  private static void setParams(Map<String, ?> map, List<? super String> inputParams) {
    final Set<String> set = map.keySet();
    for (String s : set) {
      final Object value = map.get(s);
      if (value == Boolean.TRUE) {
        inputParams.add(s);
      } else if (value == Boolean.FALSE) {
        inputParams.add("no-" + s);
      } else if (value != null) {
        inputParams.add(s + "=" + value);
      }
    }
  }

  public JComponent getRoot() {
    return myRoot;
  }

  public JComponent getPreferredFocusedComponent() {
    return myOutputDestination;
  }

  public void addPropertyChangeListener(String name, PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(name, listener);
  }

  public void showAdvancedSettings() {
    final AdvancedOptionsDialog dialog = new AdvancedOptionsDialog(myProject, myInputType, getOutputType());
    dialog.setOptions(myInputOptions, myOutputOptions);

    if (dialog.showAndGet()) {
      myInputOptions = dialog.getInputOptions();
      myOutputOptions = dialog.getOutputOptions();
    }
  }

  public boolean hasAdvancedSettings() {
    return getOutputType() == SchemaType.XSD || myInputType == SchemaType.DTD;
  }
}