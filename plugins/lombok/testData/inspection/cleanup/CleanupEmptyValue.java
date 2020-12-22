package inspection.cleanup;

import lombok.Cleanup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CleanupEmptyValue {
  public static void main(String[] args) throws IOException {
    <error descr="'@Cleanup': value cannot be the empty string">@Cleanup(value = "")</error> InputStream in = new FileInputStream(args[0]);
  }
}
