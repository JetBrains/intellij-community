public class GetterLombokCaller {

  public void useGetter() {
    GetterLombok obj = new GetterLombok("Test", 25);
    String name = obj.getName();
    int age = obj.getAge();
    System.out.println("Name: " + name + ", Age: " + age);
  }

  public String fetchName(GetterLombok lombok) {
    return lombok.getName();
  }
}
