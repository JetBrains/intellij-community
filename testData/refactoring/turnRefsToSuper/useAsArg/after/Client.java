
import javax.swing.*;

public class Client {
  {
    AClass aClass = new AClass();
    SwingUtilities.invokeLater(aClass);
  }
  
  void method(void) {
     Runnable r = new AClass();
     SwingUtilities.invokeLater(r);
  }
}
