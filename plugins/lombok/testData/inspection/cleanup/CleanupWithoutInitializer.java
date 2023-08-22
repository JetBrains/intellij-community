package inspection.cleanup;

import lombok.Cleanup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CleanupWithoutInitializer {
  public static void main(String[] args) throws IOException {
    <error descr="@Cleanup variable declarations need to be initialized.">@Cleanup</error> InputStream in;
  }
}
