package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import org.apache.velocity.runtime.parser.ParseException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;

/*
 * @author: MYakovlev
 */

public class CreateFromTemplatePanel{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.ui.CreateFromTemplatePanel");

  private FileTemplate myTemplate;
  private JPanel myMainPanel;
  private JPanel myAttrPanel;
  private JLabel myFilenameLabel = new JLabel("File name:");
  private JTextField myFilenameField = new JTextField();
  private Properties myPredefinedProperties = new Properties();
  private ArrayList<Pair<String, JTextField>> myAttributes = new ArrayList<Pair<String,JTextField>>();

  private int myLastRow = 0;

  private int myHorisontalMargin = -1;
  private int myVerticalMargin = -1;

  public CreateFromTemplatePanel(FileTemplate template, Properties predefinedProperties){
    myTemplate = template;
    myPredefinedProperties = predefinedProperties;
  }

/*
  public void disposeUIResources(){
    myMainPanel = null;
    myAttrPanel = null;
    myAttributes = null;
  }
*/

  public JComponent getComponent() throws ParseException {
    if (myMainPanel == null){
      myMainPanel = new JPanel(new GridBagLayout()){
        public Dimension getPreferredSize(){
          return getMainPanelPreferredSize(super.getPreferredSize());
        }
      };
      myAttrPanel = new JPanel(new GridBagLayout());
      JPanel myScrollPanel = new JPanel(new GridBagLayout());
      updateShown();

      myScrollPanel.setBorder(null);
      myScrollPanel.add(myAttrPanel,  new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2), 0, 0));
      myScrollPanel.add(new JPanel(), new GridBagConstraints(0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
      JScrollPane attrScroll = new JScrollPane(myScrollPanel);
      attrScroll.setViewportBorder(null);

      myMainPanel.add(attrScroll, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    }
    return myMainPanel;
  }

  public void ensureFitToScreen(int horisontalMargin, int verticalMargin){
    myHorisontalMargin = horisontalMargin;
    myVerticalMargin = verticalMargin;
  }

  private Dimension getMainPanelPreferredSize(Dimension superPreferredSize){
    if((myHorisontalMargin > 0) && (myVerticalMargin > 0)){
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension preferredSize = superPreferredSize;
      Dimension maxSize = new Dimension(screenSize.width - myHorisontalMargin, screenSize.height - myVerticalMargin);
      int width = Math.min(preferredSize.width, maxSize.width);
      int height = Math.min(preferredSize.height, maxSize.height);
      if(height < preferredSize.height){
        width = Math.min(width + 50, maxSize.width); // to disable horizontal scroller
      }
      preferredSize = new Dimension(width, height);
      return preferredSize;
    }
    else{
      return superPreferredSize;
    }
  }

  private void updateShown() throws ParseException{
    String[] attributes = ArrayUtil.EMPTY_STRING_ARRAY;
    ParseException thrownException = null;

    try {
      attributes = myTemplate.getUnsetAttributes(myPredefinedProperties);
      Arrays.sort(attributes);
    }
    catch (ParseException e) {
      thrownException = e;
    }

    Insets insets = new Insets(2, 2, 2, 2);
    myAttrPanel.add(Box.createHorizontalStrut(200), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 0));
    if(!myTemplate.isJavaClassTemplate()){
      myAttrPanel.add(myFilenameLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
      myAttrPanel.add(myFilenameField, new GridBagConstraints(0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
    }
    else{
      myAttrPanel.remove(myFilenameLabel);
      myAttrPanel.remove(myFilenameField);
    }

    for (int i = 0; i < attributes.length; i++){
      String attribute = attributes[i];
      JLabel label = new JLabel(attribute.replace('_', ' ') + ":");
      JTextField field = new JTextField();
      myAttributes.add(new Pair<String,JTextField> (attribute, field));
      myAttrPanel.add(label,     new GridBagConstraints(0, myLastRow*2+3, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
      myAttrPanel.add(field, new GridBagConstraints(0, myLastRow*2+4, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
      myLastRow++;
    }

    myAttrPanel.repaint();
    myAttrPanel.revalidate();
    myMainPanel.revalidate();
    if(thrownException != null){
      throw thrownException;
    }
  }

  public String getFileName(){
    String fileName = myFilenameField.getText();
    return fileName == null ? "" : fileName;
  }

  public Properties getProperties(){
    Properties result = (Properties) myPredefinedProperties.clone();
    for (Iterator<Pair<String,JTextField>> i = myAttributes.iterator(); i.hasNext();) {
      Pair<String,JTextField> pair = i.next();
      result.put(pair.first, pair.second.getText());
    }
    return result;
  }
}

