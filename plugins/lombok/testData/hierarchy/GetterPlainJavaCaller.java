public class GetterPlainJavaCaller {

  public void useGetter() {
    GetterPlainJava obj = new GetterPlainJava("Test", 25);
    String name = obj.getName();
    int age = obj.getAge();
    System.out.println("Name: " + name + ", Age: " + age);
  }

  public String fetchName(GetterPlainJava plainJava) {
    return plainJava.getName();
  }
}
