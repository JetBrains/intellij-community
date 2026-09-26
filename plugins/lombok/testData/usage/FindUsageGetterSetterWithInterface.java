import lombok.Data;

interface FindUsageGetterSetterWithInterfaceService {
  String getBar();
  void setBar(String bar);
}

@Data
class FindUsageGetterSetterWithInterfaceImpl implements FindUsageGetterSetterWithInterfaceService {
  private String b<caret>ar;
}

class FindUsageGetterSetterWithInterfaceUser {
  public static void main(String[] args) {
    FindUsageGetterSetterWithInterfaceService service = new FindUsageGetterSetterWithInterfaceImpl();
    service.setBar("myBar");
    System.out.println("Bar is: " + service.getBar());
    FindUsageGetterSetterWithInterfaceImpl impl = new FindUsageGetterSetterWithInterfaceImpl();
    impl.setBar("directBar");
    System.out.println("Bar is: " + impl.getBar());
  }
}
