package inspection.cleanup;

import lombok.Cleanup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CleanupWrongElement {

  public static void main(String[] args) throws IOException {
    InputStream in = new FileInputStream("name");

    for (<error descr="'@Cleanup': is legal only on a local variable declaration inside a block">@Cleanup</error> int i = 0; i < 10; i++) {

    }
  }
}
