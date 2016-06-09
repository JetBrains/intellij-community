package de.plushnikov.bug.issue180;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Builder;
import lombok.val;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlEnumValue;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@ToString
public class AddressImpl extends AddressBase {

  private String countryCode;
  private int countryCode2;
  @XmlElementRef
  private float countryCode3;
  private char countryCode4;
  private double countryCode5;
  @XmlEnumValue("x")
  private Double countryCode6;
  private Character countryCode7;
  private Float countryCode8;
  @XmlAnyElement
  private Integer countryCode9;
  private Long countryCode10;
  @Generated("")
  private AddressImpl countryCode11;
  @XmlAttribute
  private AddressBase countryCode12;

  @Builder
  public AddressImpl(String addressLine1, String addressLine2, String addressLine3, String city,
                     String postalCode, String countryCode, int countryCode2,
                     float countryCode3, char countryCode4, double countryCode5,
                     Double countryCode6, Character countryCode7,
                     Float countryCode8, Integer countryCode9,
                     Long countryCode10, AddressImpl countryCode11,
                     AddressBase countryCode12) {
    super(addressLine1, addressLine2, addressLine3, city, postalCode);

    val x = countryCode12;
    if (null != x) {
      x.getCountryCode();
    }

    val y = countryCode11;
    y.setCountryCode12(x);

    this.countryCode = countryCode;
  }

}