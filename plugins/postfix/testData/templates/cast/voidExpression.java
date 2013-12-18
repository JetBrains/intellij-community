public class Foo {
    void m(Object o) {
        bar().cast<caret>
    }
  
    void bar() {}
}