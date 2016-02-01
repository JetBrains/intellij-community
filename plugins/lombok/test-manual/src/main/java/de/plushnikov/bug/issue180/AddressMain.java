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
  }
}
