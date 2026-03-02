import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor(staticName = "of")
class WithLombok<T> {
  private List<T> list;

  public List<T> getList(){
    return list;
  }
}

class Test {

  static void main() {
    // Ok
    WithLombok<String> with1 = WithLombok.of(List.<String>of());
    System.out.println(with1);
    System.out.println(with1.getList());

    // Should not be: Incompatible types. Found: 'WithLombok<java.lang.Object>', required: 'WithLombok<java.lang.String>'
    WithLombok<String> with2 = WithLombok.of(List.of());
    System.out.println(with2);
  }
}
