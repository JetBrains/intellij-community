package de.plushnikov.bug.issue180;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public abstract class AddressBase {

  private String addressLine1;

  private String addressLine2;

  private String addressLine3;

  private String city;

  private String postalCode;

  public AddressBase(String addressLine1, String addressLine2, String addressLine3, String city, String postalCode) {
    this.addressLine1 = addressLine1;
    this.addressLine2 = addressLine2;
    this.addressLine3 = addressLine3;
    this.city = city;
    this.postalCode = postalCode;
  }

  public abstract String getCountryCode();

  public abstract void setCountryCode(String countryCode);
}