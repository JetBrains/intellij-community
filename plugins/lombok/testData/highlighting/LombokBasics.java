import lombok.*;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public final class LombokBasics {
  @Getter @Setter
  // should not be marked as unused
  private int age = <warning descr="Variable 'age' initializer '10' is redundant">10</warning>;

  void test(LombokBasics other) {
    setAge(20); // setter should be resolved
    System.out.println(getAge()); // getter should be resolved
    System.out.println(this.toString()); // toString is defined
    if (this <warning descr="Object values are compared using '==', not 'equals()'">==</warning> other) {} // equals is implicitly defined
  }

  public static void main(String[] args) {
    new LombokBasics(10).test(new LombokBasics(12));
  }
}
@AllArgsConstructor
class <warning descr="Class 'FinalCheck' is never used">FinalCheck</warning> {
  @Getter
  private int a;
  @Setter
  private int <warning descr="Private field 'b' is assigned but never accessed">b</warning>;
  @Getter @Setter
  private int c;
}
final class <warning descr="Class 'Foo' is never used">Foo</warning> {
  @Getter
  String bar;

  public void <warning descr="Method 'test()' is never used">test</warning>() {
    bar = null;
    System.out.println(getBar().<warning descr="Method invocation 'trim' will produce 'NullPointerException'">trim</warning>());
  }
}
class <warning descr="Class 'Outer' is never used">Outer</warning> {
  void <warning descr="Method 'foo()' is never used">foo</warning>() {
@EqualsAndHashCode
class <warning descr="Local class 'Inner' is never used">Inner</warning> {
  int x;
  }
  }
  }
class <warning descr="Class 'IntellijInspectionNPEDemo' is never used">IntellijInspectionNPEDemo</warning> {

  @Builder
  public static class <warning descr="Class 'SomeDataClass' is never used">SomeDataClass</warning> {
    public static class <warning descr="Class 'SomeDataClassBuilder' is never used">SomeDataClassBuilder</warning> {
      private void <warning descr="Private method 'buildWithJSON()' is never used">buildWithJSON</warning>() {
        this.jsonObject = "test";
      }
    }

    public final String jsonObject;
  }

}
class <warning descr="Class 'InitializerInVar' is never used">InitializerInVar</warning> {
public  void <warning descr="Method 'foo()' is never used">foo</warning>() {
  var x = 1; // expect no "not used initializer" warning
  try { x = 3; } catch (NullPointerException e) { x = 1; }
  System.out.println(x);
  }
  }
