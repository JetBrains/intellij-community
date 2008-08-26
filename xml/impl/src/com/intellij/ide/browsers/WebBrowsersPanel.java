package com.intellij.ide.browsers;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * @author spleaner
 */
public class WebBrowsersPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.WebBrowsersPanel");

  private JPanel mySettingsPanel;
  private Map<BrowsersConfiguration.BrowserFamily, Pair<String, Boolean>> myBrowserToPathMap;
  private Map<BrowsersConfiguration.BrowserFamily, Pair<JCheckBox, TextFieldWithBrowseButton>> myBrowserSettingsMap =
      new HashMap<BrowsersConfiguration.BrowserFamily, Pair<JCheckBox, TextFieldWithBrowseButton>>();

  public WebBrowsersPanel(final Map<BrowsersConfiguration.BrowserFamily, Pair<String, Boolean>> browserToPathMap) {
    setLayout(new BorderLayout());

    myBrowserToPathMap = browserToPathMap;

    mySettingsPanel = new JPanel();
    mySettingsPanel.setLayout(new BoxLayout(mySettingsPanel, BoxLayout.Y_AXIS));
    mySettingsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    add(mySettingsPanel, BorderLayout.NORTH);

    createIndividualSettings(BrowsersConfiguration.BrowserFamily.FIREFOX, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.EXPLORER, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.SAFARI, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.OPERA, mySettingsPanel);
  }

  private void createIndividualSettings(@NotNull final BrowsersConfiguration.BrowserFamily family, final JPanel container) {
    final JPanel result = new JPanel();

    result.setBorder(BorderFactory.createTitledBorder(family.getName()));

    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    final TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
    field.setEditable(false);

    FileChooserDescriptor descriptor = SystemInfo.isMac
                                       ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                       : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    field.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, descriptor);

    result.add(field);

    final JPanel buttonsPanel = new JPanel(new BorderLayout());

    final JPanel activePanel = new JPanel();
    activePanel.setLayout(new BoxLayout(activePanel, BoxLayout.X_AXIS));

    final JCheckBox checkBox = new JCheckBox();
    activePanel.add(checkBox);
    final JLabel label = new JLabel(XmlBundle.message("browser.active"));
    label.setLabelFor(checkBox);
    activePanel.add(label);

    buttonsPanel.add(activePanel, BorderLayout.WEST);
    final JButton resetButton = new JButton(XmlBundle.message("browser.default.settings"));
    resetButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        field.getTextField().setText(family.getExecutionPath());
      }
    });
    buttonsPanel.add(resetButton, BorderLayout.EAST);

    result.add(buttonsPanel);
    container.add(result);

    final Pair<String, Boolean> settings = myBrowserToPathMap.get(family);
    field.getTextField().setText(settings.first);
    checkBox.setSelected(settings.second.booleanValue());

    myBrowserSettingsMap.put(family, Pair.create(checkBox, field));
  }

  public void dispose() {
    myBrowserSettingsMap = null;
    myBrowserToPathMap = null;
  }

  public boolean isModified() {
    for (BrowsersConfiguration.BrowserFamily family : BrowsersConfiguration.BrowserFamily.values()) {
      final Pair<String, Boolean> old = myBrowserToPathMap.get(family);
      final Pair<JCheckBox, TextFieldWithBrowseButton> settings = myBrowserSettingsMap.get(family);

      if (old.second.booleanValue() != settings.first.isSelected() || !old.first.equals(settings.second.getText())) {
        return true;
      }
    }

    return false;
  }

  public void apply() {
    for (BrowsersConfiguration.BrowserFamily family : myBrowserSettingsMap.keySet()) {
      final Pair<JCheckBox, TextFieldWithBrowseButton> buttonPair = myBrowserSettingsMap.get(family);
      myBrowserToPathMap.put(family, Pair.create(buttonPair.second.getText(), buttonPair.first.isSelected()));
    }
  }

  public void reset() {
    for (BrowsersConfiguration.BrowserFamily family : myBrowserToPathMap.keySet()) {
      final Pair<JCheckBox, TextFieldWithBrowseButton> buttonPair = myBrowserSettingsMap.get(family);
      final Pair<String, Boolean> pair = myBrowserToPathMap.get(family);
      buttonPair.first.setSelected(pair.second.booleanValue());
      buttonPair.second.getTextField().setText(pair.first);
    }
  }
}
