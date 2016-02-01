package de.plushnikov.bug.issue180;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Builder;

@NoArgsConstructor
@Getter
@Setter
public class AddressImpl extends AddressBase {

  private String countryCode;

  @Builder
  public AddressImpl(String addressLine1, String addressLine2, String addressLine3, String city,
                     String postalCode, String countryCode) {
    super(addressLine1, addressLine2, addressLine3, city, postalCode);

    this.countryCode = countryCode;
  }

}