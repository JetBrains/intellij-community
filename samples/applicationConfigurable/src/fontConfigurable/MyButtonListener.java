package fontConfigurable;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Aug 7, 2010
 * Time: 9:36:45 PM
 */
public class MyButtonListener implements ActionListener {


    public JButton myButton;
    public JComboBox myFontCombo;
    public JComboBox myFontSize;


    public void actionPerformed(ActionEvent e) {

        UISettings settings = UISettings.getInstance();
        LafManager lafManager = LafManager.getInstance();
        // Restore default font
        settings.FONT_FACE = "Segoe UI";
        settings.FONT_SIZE = 12;
        myFontCombo.setSelectedItem(settings.FONT_FACE);
        myFontSize.setSelectedItem(String.valueOf(settings.FONT_SIZE));
        settings.fireUISettingsChanged();
        lafManager.updateUI();


    }

}

