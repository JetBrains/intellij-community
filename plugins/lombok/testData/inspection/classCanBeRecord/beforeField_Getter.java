// "Convert to record class" "true-preview"

class Person<caret> {
  @lombok.Getter final String name;
  @lombok.Getter final int age;

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
