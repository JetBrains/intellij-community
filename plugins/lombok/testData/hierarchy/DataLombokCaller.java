public class DataLombokCaller {

  public void useData() {
    DataLombok obj = new DataLombok();
    obj.setId(1L);
    obj.setName("Test");
    obj.setActive(true);

    Long id = obj.getId();
    String name = obj.getName();
    boolean active = obj.isActive();

    System.out.println("ID: " + id + ", Name: " + name + ", Active: " + active);
  }

  public Long fetchId(DataLombok data) {
    return data.getId();
  }

  public void updateId(DataLombok data, Long newId) {
    data.setId(newId);
  }
}
