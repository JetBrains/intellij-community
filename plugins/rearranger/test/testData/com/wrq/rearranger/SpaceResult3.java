import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SpaceTest3 {
  void method() {
    final JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        int i = 0; // logger.debug("cancel button pressed");
        String s = "cancelling"; // filename.setText("cancelling...");
        // cancellableObject.setCancel();
      }
    });
    JPanel progressPanel = new JPanel();
    progressPanel.add(cancelButton);
  }
}
