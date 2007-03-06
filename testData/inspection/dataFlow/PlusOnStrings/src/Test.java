import java.io.*;

public class Foo {
  public void foo(Object a, String b) {
     String c = a + b;
     if (c == null) {
         System.out.println("Can't be!");
     }
  }
}