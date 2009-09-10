import javax.swing.*;
import java.awt.*;

public class ChainedConstructorTest {
  private JComponent myRootComponent;
  public JScrollPane myScrollPane;
  public JList myList;

  public ChainedConstructorTest() {
    this(null, false);
  }

  public ChainedConstructorTest(String[] names, boolean mode) {
    myList = new JList();
    myScrollPane.setViewportView(myList);
  }
}