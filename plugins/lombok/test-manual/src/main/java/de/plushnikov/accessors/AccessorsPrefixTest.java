package de.plushnikov.accessors;

@lombok.experimental.Accessors(prefix = {"_", "$", "m_", "f", "b"})
public class AccessorsPrefixTest {
  @lombok.Setter
  private String _underscore;
  @lombok.Setter
  private String $DollarSign;
  @lombok.Setter
  private String m_fieldName;
  @lombok.Setter
  private String foo;
  @lombok.Setter
  private String bAr;
}