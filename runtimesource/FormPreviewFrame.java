
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class FormPreviewFrame {
  private JComponent myComponent;

  // Note: this class should not be obfuscated

  public static void main(String[] args) {
    FormPreviewFrame f = new FormPreviewFrame();

    JFrame frame = new JFrame("Form Preview");
    frame.setContentPane(f.myComponent);

    // Add menu bar
    final JMenuBar menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);

    final JMenu menuFile = new JMenu("File");
    menuFile.setMnemonic(KeyEvent.VK_F);
    final JMenuItem menuItemExit = new JMenuItem(new MyExitAction());
    menuFile.add(menuItemExit);
    menuBar.add(menuFile);

    final JMenu viewMenu = new JMenu("View");
    viewMenu.setMnemonic(KeyEvent.VK_V);
    viewMenu.add(new JMenuItem(new MyPackAction(frame)));
    viewMenu.addSeparator();
    menuBar.add(viewMenu);

    final UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
    for(int i = 0; i < lafs.length; i++){
      viewMenu.add(new MySetLafAction(frame, lafs[i]));
    }

    frame.pack();
    frame.addWindowListener(new MyWindowListener());
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setLocation((screenSize.width - frame.getWidth())/2, (screenSize.height - frame.getHeight())/2);
    frame.show();
  }

  private static final class MyWindowListener extends WindowAdapter{
    public void windowClosing(final WindowEvent e) {
      System.exit(0);
    }
  }

  private static final class MyExitAction extends AbstractAction{
    public MyExitAction() {
      super("Exit");
    }

    public void actionPerformed(final ActionEvent e) {
      System.exit(0);
    }
  }

  private static final class MyPackAction extends AbstractAction{
    private final JFrame myFrame;

    public MyPackAction(final JFrame frame) {
      super("Pack");
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
          "Cannot change LookAndFeel.\nReason: " + exc.getMessage(),
          "Error",
          JOptionPane.ERROR_MESSAGE
        );
      }
    }
  }
}
