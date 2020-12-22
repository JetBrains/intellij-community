package de.plushnikov.sneakythrows;

import java.io.File;

public class Issue394 {

//  @SneakyThrows
  public String calcString() {
    new File("somePath").createNewFile();
    return "";
  }

  public static void main(String[] args) {
    new Issue394().calcString();
  }
}
