class Entity {
  private final String field;

  private Entity(String field) {
    this.field = field;
  }

  public static Entity of(String field) {
    return new Entity(field);
  }

  public String getField() {
    return this.field;
  }

  @Override
  public String toString() {
    return "Entity(field=" + this.field + ")";
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final java.lang.Object $field = this.field;
    result = result * PRIME + ($field == null ? 0 : $field.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (o == this) return true;

    if (!(oinstanceofEntity)) return false;

    final Entity other = (Entity) o;
    if (!other.canEqual((java.lang.Object) this)) return false;

    final java.lang.Object this$field = this.field;
    final java.lang.Object other$field = other.field;
    if (this$field == null ? other$field != null : !this$field.equals(other$field)) return false;

    return true;
  }

  public boolean canEqual(final Object other) {
    return other instanceof Entity;
  }
}