public class Foo {
  int myOffset;
  void foo() {
    int offset = myOffset + 4;
    byte[] a = new byte[10];
    byte b1 = a[offset++];
    byte b2 = a[offset++];
    System.out.println(b1);
    System.out.println(b2);
  }
}