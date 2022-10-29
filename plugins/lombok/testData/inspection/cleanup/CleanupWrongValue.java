package inspection.cleanup;

import lombok.Cleanup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CleanupWrongValue {
  public static void main(String[] args) throws IOException {
    <error descr="@Cleanup: method 'notExist()' not found on target class">@Cleanup(value = "notExist")</error> InputStream in = new FileInputStream(args[0]);
  }
}
