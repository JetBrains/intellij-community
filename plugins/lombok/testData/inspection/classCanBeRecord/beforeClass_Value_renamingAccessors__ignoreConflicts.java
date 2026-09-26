// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873

@lombok.Value
class Person<caret> {
  String name;
  int age;
}

class Foo {
  void foo() {
    Person person = new Person("Charlie", 42);
    // Renaming getters to accessors requires the Lombok plugin
    System.out.println("Person " + person.getName() + " is " + person.getAge() + " years old");
  }
}
