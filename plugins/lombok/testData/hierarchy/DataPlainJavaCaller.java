public class DataPlainJavaCaller {

  public void useData() {
    DataPlainJava obj = new DataPlainJava();
    obj.setId(1L);
    obj.setName("Test");
    obj.setActive(true);

    Long id = obj.getId();
    String name = obj.getName();
    boolean active = obj.isActive();

    System.out.println("ID: " + id + ", Name: " + name + ", Active: " + active);
  }

  public Long fetchId(DataPlainJava data) {
    return data.getId();
  }

  public void updateId(DataPlainJava data, Long newId) {
    data.setId(newId);
  }
}
