import javax.swing.*;

/**
 * Only {@code public} is a real bean property. {@code java.beans.Introspector} ignores the
 * non-public and static accessor pairs below, and so must the ASM-based provider.
 */
public class NonPublicAccessorsComponent extends JPanel {
  private static String staticProperty = "";
  private String privateProperty = "";
  private String protectedProperty = "";
  private String packageLocalProperty = "";
  private String publicProperty = "";

  public String getPublicProperty() {
    return publicProperty;
  }

  public void setPublicProperty(String value) {
    publicProperty = value;
  }

  public static String getStaticProperty() {
    return staticProperty;
  }

  public static void setStaticProperty(String value) {
    staticProperty = value;
  }

  private String getPrivateProperty() {
    return privateProperty;
  }

  private void setPrivateProperty(String value) {
    privateProperty = value;
  }

  protected String getProtectedProperty() {
    return protectedProperty;
  }

  protected void setProtectedProperty(String value) {
    protectedProperty = value;
  }

  String getPackageLocalProperty() {
    return packageLocalProperty;
  }

  void setPackageLocalProperty(String value) {
    packageLocalProperty = value;
  }
}
