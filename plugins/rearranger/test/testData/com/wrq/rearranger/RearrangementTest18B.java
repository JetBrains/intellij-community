public class MethodEntry {
  private boolean isSetter;

  public boolean isGetter() {
    return isGetter;
  }

  public boolean isIsSetter() {
    return isSetter;
  }

  public String getCustomizedPrecedingComment() {
    if (customizedPrecedingComment == null) {
      return "";
    }
    return "comment";
  }

  public DefaultMutableTreeNode addToPopupTree() {
    if (isSetter()) return null;
    return null;
  }

  public boolean isSetter() {
    return isSetter;
  }
}