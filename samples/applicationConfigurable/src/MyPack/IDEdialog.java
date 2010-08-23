package MyPack;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class IDEdialog extends JDialog {
    private JPanel contentPane;
    private JPanel Name;
    public JComboBox myFontCombo;
    public JComboBox myFontSize;
    public JLabel menuFontSettingsLabel;
    public JButton ButtonRestoreDefaultFont;
    private String Buffer;


    public IDEdialog() {
        setContentPane(contentPane);
        setModal(true);
        UISettings settings = UISettings.getInstance();
        myFontCombo.setModel(new DefaultComboBoxModel(UIUtil.getValidFontNames(false)));
        myFontSize.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
        myFontCombo.setSelectedItem(settings.FONT_FACE);
        myFontSize.setSelectedItem( String.valueOf(settings.FONT_SIZE));

// Configure the Set Default Font button.

        MyButtonListener actionListener = new MyButtonListener();
        actionListener.button=ButtonRestoreDefaultFont;
        actionListener.myFontCombo= myFontCombo;
        actionListener.myFontSize= myFontSize;
        ButtonRestoreDefaultFont.addActionListener(actionListener);


// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
// add your code here
       

        dispose();
    }

    
    private void onCancel() {
// add your code here if necessary
        dispose();
    }



    public static void main(String[] args) {
        IDEdialog dialog = new IDEdialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }


}
