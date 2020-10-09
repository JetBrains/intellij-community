package de.plushnikov.bug.issue180;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnegative;
import javax.annotation.WillNotClose;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public abstract class AddressBase {

  @Nonnegative
  private String addressLine1;

  @NotNull
  private String addressLine2;

  @DecimalMax("1")
  private String addressLine3;

  @DecimalMin("1")
  private String city;

  @WillNotClose
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