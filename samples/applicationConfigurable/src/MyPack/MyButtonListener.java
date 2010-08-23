package MyPack;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Aug 7, 2010
 * Time: 9:36:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyButtonListener implements ActionListener {


    public JButton button;
    public JComboBox myFontCombo;
    public JComboBox myFontSize;


      public void actionPerformed(ActionEvent e) {

       UISettings settings = UISettings.getInstance();
       LafManager lafManager = LafManager.getInstance();
       settings.FONT_FACE="Segoe UI";
       settings.FONT_SIZE=12;
       myFontCombo.setSelectedItem(settings.FONT_FACE);
       myFontSize.setSelectedItem( String.valueOf(settings.FONT_SIZE));
       settings.fireUISettingsChanged();
       lafManager.updateUI();

         


      }

 }

