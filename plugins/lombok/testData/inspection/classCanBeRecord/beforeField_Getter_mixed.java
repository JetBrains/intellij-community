// "Convert to record class" "false"

class Person<caret> {
  @lombok.Getter final String name;
  @lombok.Getter int age; // we don't want to suggest converting this class to record, because this field is not final

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
