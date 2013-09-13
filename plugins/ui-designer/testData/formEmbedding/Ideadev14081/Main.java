/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class Main {
  private JComponent myComponent;
  private static ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  // Note: this class should not be obfuscated

  public static void main(String[] args) {
    Main f = new Main();

    JFrame frame = new JFrame(ourBundle.getString("form.preview.title"));
    frame.setContentPane(f.myComponent);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Add menu bar
    final JMenuBar menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);

    final JMenu menuFile = new JMenu(ourBundle.getString("form.menu.preview"));
    menuFile.setMnemonic(ourBundle.getString("form.menu.preview.mnemonic").charAt(0));
    menuFile.add(new JMenuItem(new MyPackAction(frame)));
    menuFile.add(new JMenuItem(new MyExitAction()));
    menuBar.add(menuFile);

    final JMenu viewMenu = new JMenu(ourBundle.getString("form.menu.laf"));
    viewMenu.setMnemonic(ourBundle.getString("form.menu.laf.mnemonic").charAt(0));
    menuBar.add(viewMenu);

    final UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
    for(int i = 0; i < lafs.length; i++){
      viewMenu.add(new MySetLafAction(frame, lafs[i]));
    }
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private static final class MyExitAction extends AbstractAction{
    public MyExitAction() {
      super(ourBundle.getString("form.menu.file.exit"));
    }

    public void actionPerformed(final ActionEvent e) {
      System.exit(0);
    }
  }

  private static final class MyPackAction extends AbstractAction{
    private final JFrame myFrame;

    public MyPackAction(final JFrame frame) {
      super(ourBundle.getString("form.menu.view.pack"));
      myFrame = frame;
    }

    public void actionPerformed(final ActionEvent e) {
      myFrame.pack();
    }
  }

  private static final class MySetLafAction extends AbstractAction{
    private final JFrame myFrame;
    private final UIManager.LookAndFeelInfo myInfo;

    public MySetLafAction(final JFrame frame, final UIManager.LookAndFeelInfo info) {
      super(info.getName());
      myFrame = frame;
      myInfo = info;
    }

    public void actionPerformed(ActionEvent e) {
      try{
        UIManager.setLookAndFeel(myInfo.getClassName());
        SwingUtilities.updateComponentTreeUI(myFrame);
        Dimension prefSize = myFrame.getPreferredSize();
        if(prefSize.width > myFrame.getWidth() || prefSize.height > myFrame.getHeight()){
          myFrame.pack();
        }
      }
      catch(Exception exc){
        JOptionPane.showMessageDialog(
          myFrame,
          MessageFormat.format(ourBundle.getString("error.cannot.change.look.feel"), new Object[] {exc.getMessage()}),
          ourBundle.getString("error.title"),
          JOptionPane.ERROR_MESSAGE
        );
      }
    }
  }
}
