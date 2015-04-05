public class Zoo {
  private String meerkat;
  private String warthog;

  @java.beans.ConstructorProperties({"meerkat", "warthog"})
  public Zoo(String meerkat, String warthog) {
    this.meerkat = meerkat;
    this.warthog = warthog;
  }

  public static ZooBuilder builder() {
    return new ZooBuilder();
  }

  public Zoo create() {
    return new Zoo("tomon", "pumbaa");
  }

  public String getMeerkat() {
    return this.meerkat;
  }

  public String getWarthog() {
    return this.warthog;
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Zoo)) return false;
    final Zoo other = (Zoo) o;
    final Object this$meerkat = this.meerkat;
    final Object other$meerkat = other.meerkat;
    if (this$meerkat == null ? other$meerkat != null : !this$meerkat.equals(other$meerkat)) return false;
    final Object this$warthog = this.warthog;
    final Object other$warthog = other.warthog;
    if (this$warthog == null ? other$warthog != null : !this$warthog.equals(other$warthog)) return false;
    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $meerkat = this.meerkat;
    result = result * PRIME + ($meerkat == null ? 0 : $meerkat.hashCode());
    final Object $warthog = this.warthog;
    result = result * PRIME + ($warthog == null ? 0 : $warthog.hashCode());
    return result;
  }

  public String toString() {
    return "Zoo(meerkat=" + this.meerkat + ", warthog=" + this.warthog + ")";
  }

  public static class ZooBuilder {
    private String meerkat;
    private String warthog;

    ZooBuilder() {
    }

    public Zoo.ZooBuilder meerkat(String meerkat) {
      this.meerkat = meerkat;
      return this;
    }

    public Zoo.ZooBuilder warthog(String warthog) {
      this.warthog = warthog;
      return this;
    }

    public Zoo build() {
      return new Zoo(meerkat, warthog);
    }

    public String toString() {
      return "Zoo.ZooBuilder(meerkat=" + this.meerkat + ", warthog=" + this.warthog + ")";
    }
  }
}