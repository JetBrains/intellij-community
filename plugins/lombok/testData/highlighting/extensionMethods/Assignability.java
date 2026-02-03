
import lombok.experimental.ExtensionMethod;
import java.util.List;
@ExtensionMethod({Ex.class})
class Assignability {
  void m1(List<String> l) {
    l.print();
  }

  void m2(List<? extends String> l) {
    l.print();
  }

  void m3(List<? super String> l) {
    l.<error descr="Cannot resolve method 'print' in 'List'">print</error>();
  }

  void m4(List<Object> l) {
    l.<error descr="Cannot resolve method 'print' in 'List'">print</error>();
  }

  void m5(List l) {
    l.print();
  }

  <T> void m6(List<T> l) {
    l.<error descr="Cannot resolve method 'print' in 'List'">print</error>();
  }

}

class Ex {
  public static void print(List<? extends String> list) {
  }
}
