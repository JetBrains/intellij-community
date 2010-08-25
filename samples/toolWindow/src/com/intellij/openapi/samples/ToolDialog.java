package com.intellij.openapi.samples;

import com.intellij.openapi.wm.ToolWindow;

import javax.swing.*;
import java.awt.event.*;
import java.util.Calendar;

public class ToolDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonRefresh;
    private JButton buttonHide;
    
    private JLabel Date22;
    private JLabel Time22;
    private JLabel TimeZone;
    public ToolWindow toolWin;
    


    public ToolDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonRefresh);



        

        buttonRefresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonHide.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

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
// Refresh tool window.
        this.currentDateTime();
        dispose();
    }

    private void onCancel() {
// Hide tool window.
         toolWin.hide(null);
        dispose();
    }

    public JPanel getPanel() {
        return contentPane;
    }

    public void currentDateTime()
    {
     // Get current date and time
       Calendar instance = Calendar.getInstance();
       Date22.setText(String.valueOf(instance.get(Calendar.DAY_OF_MONTH)) + "/"
       + String.valueOf(instance.get(Calendar.MONTH)+1) + "/" +  String.valueOf(instance.get(Calendar.YEAR)) );
       Date22.setIcon(new ImageIcon(getClass().getResource("/com/intellij/openapi/samples/Calendar-icon.png")));
       int min = instance.get(Calendar.MINUTE);
       String strMin;
       if ( min < 10) {strMin = "0" + String.valueOf(min);}
         else { strMin = String.valueOf(min);}
       Time22.setText(instance.get(Calendar.HOUR_OF_DAY) + ":" + strMin);
       Time22.setIcon(new ImageIcon(getClass().getResource("/com/intellij/openapi/samples/Time-icon.png")));
     // Get time zone
       long gmt_Offset = instance.get(Calendar.ZONE_OFFSET); // offset from GMT in milliseconds
       String str_gmt_Offset = String.valueOf(gmt_Offset/3600000);
       str_gmt_Offset = (gmt_Offset > 0) ? "GMT + " + str_gmt_Offset :  "GMT - " + str_gmt_Offset;
       TimeZone.setText(str_gmt_Offset);
       TimeZone.setIcon(new ImageIcon(getClass().getResource("/com/intellij/openapi/samples/Time-zone-icon.png"))); 



        
    }
}
