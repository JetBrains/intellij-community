package de.plushnikov.sneakythrows;

import lombok.SneakyThrows;

import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SneakyTest {
  //    @SneakyThrows(FileNotFoundException.class)
//    @SneakyThrows(IOException.class)
  @SneakyThrows
  public void readFile() {
    FileReader test = new FileReader("test");

    test.read();
  }

  @SneakyThrows
  public int read(FileReader in) {
    return in.read();
  }


  private ExecutorService executorService;

  @SneakyThrows
  private String test(Callable<String> input) {
    Future<String> submit = executorService.submit(input);
    return submit.get(10, TimeUnit.SECONDS);
  }

  @SneakyThrows
  public void foo() {
    bar();
  }

  public void bar() throws IOException, InterruptedException {
  }

  public static void main(String[] args) {

  }
}
