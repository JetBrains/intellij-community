// "Convert to record class" "true-preview"

@lombok.Getter
class Person<caret> {
  final String name;
  final int age;

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
