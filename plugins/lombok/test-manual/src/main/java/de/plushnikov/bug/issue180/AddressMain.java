package de.plushnikov.bug.issue180;

public class AddressMain {
  public static void main(String[] args) {
    final AddressImpl address = AddressImpl.builder()
        .addressLine1("")
        .addressLine2("")
        .addressLine3("")
        .countryCode("")
        .build();
    System.out.println(address);

    final AddressImpl address2 = AddressImpl.builder()
        .addressLine1("a")
        .addressLine2("b")
        .addressLine3("c")
        .countryCode("d")
        .countryCode11(address)
        .build();
    System.out.println(address2);

    final AddressImpl address3 = AddressImpl.builder()
        .addressLine1("aa")
        .addressLine2("bb")
        .addressLine3("cc")
        .countryCode("dd")
        .countryCode12(address2)
        .build();
    System.out.println(address3);

    final AddressImpl address4 = AddressImpl.builder()
        .addressLine1("aaa")
        .addressLine2("bbb")
        .addressLine3("ccc")
        .countryCode("ddd")
        .countryCode11(address3)
        .countryCode12(address2)
        .build();
    System.out.println(address4);
  }
}
