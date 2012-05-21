public class MethodEntry {
  private boolean isSetter;

  public boolean isIsSetter() {
    return isSetter;
  }
// Getters/Setters
  public String getCustomizedPrecedingComment() {
    if (customizedPrecedingComment == null) {
      return "";
    }
    return "comment";
  }

  public boolean isGetter() {
    return isGetter;
  }

  public boolean isSetter() {
    return isSetter;
  }
// Other Methods
  public DefaultMutableTreeNode addToPopupTree() {
    if (isSetter()) return null;
    return null;
  }
}