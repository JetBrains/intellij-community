import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RearrangementTest20 {

  public static void Father(final Object event) {
    JCheckBox box = new JCheckBox("text");
    box.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        ChildA(e);
      }
    };
  }
  private static void ChildA(final Object event) {
  }
}
