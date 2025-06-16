// "Convert to record class" "true-preview"

record Person(String name, int age) {
}

@Getter // another class, just to verify that the import doesn't get deleted too eagerly
class Person2 {
  final String name;
  final int age;

  Person2(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
