// "Convert to record class" "false"

class Person<caret> {
  // Java records are immutable, so we don't suggest conversion here
  @lombok.Setter String name;
  @lombok.Setter int age;

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
