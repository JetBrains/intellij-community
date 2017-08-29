import java.util.ArrayList;
import java.util.Map;

public class BuilderSingularMap {

  private java.util.Map<String, String> myMap;

  @java.beans.ConstructorProperties({"myMap"})
  BuilderSingularMap(Map<String, String> myMap) {
    this.myMap = myMap;
  }

  public static BuilderSingularMapBuilder builder() {
    return new BuilderSingularMapBuilder();
  }

  public static class BuilderSingularMapBuilder {
    private ArrayList<String> myMap$key;
    private ArrayList<String> myMap$value;

    BuilderSingularMapBuilder() {
    }

    public BuilderSingularMapBuilder myMap(String myMapKey, String myMapValue) {
      if (this.myMap$key == null) {
        this.myMap$key = new ArrayList<String>();
        this.myMap$value = new ArrayList<String>();
      }
      this.myMap$key.add(myMapKey);
      this.myMap$value.add(myMapValue);
      return this;
    }

    public BuilderSingularMapBuilder myMap(Map<? extends String, ? extends String> myMap) {
      if (this.myMap$key == null) {
        this.myMap$key = new ArrayList<String>();
        this.myMap$value = new ArrayList<String>();
      }
      for (final Map.Entry<? extends String, ? extends String> $lombokEntry : myMap.entrySet()) {
        this.myMap$key.add($lombokEntry.getKey());
        this.myMap$value.add($lombokEntry.getValue());
      }
      return this;
    }

    public BuilderSingularMapBuilder clearMyMap() {
      if (this.myMap$key != null) {
        this.myMap$key.clear();
        this.myMap$value.clear();
      }

      return this;
    }

    public BuilderSingularMap build() {
      Map<String, String> myMap;
      switch (this.myMap$key == null ? 0 : this.myMap$key.size()) {
        case 0:
          myMap = java.util.Collections.emptyMap();
          break;
        case 1:
          myMap = java.util.Collections.singletonMap(this.myMap$key.get(0), this.myMap$value.get(0));
          break;
        default:
          myMap = new java.util.LinkedHashMap<String, String>(this.myMap$key.size() < 1073741824 ? 1 + this.myMap$key.size() + (this.myMap$key.size() - 3) / 3 : Integer.MAX_VALUE);
          for (int $i = 0; $i < this.myMap$key.size(); $i++)
            myMap.put(this.myMap$key.get($i), this.myMap$value.get($i));
          myMap = java.util.Collections.unmodifiableMap(myMap);
      }

      return new BuilderSingularMap(myMap);
    }

    public String toString() {
      return "BuilderSingularMap.BuilderSingularMapBuilder(myMap$key=" + this.myMap$key + ", myMap$value=" + this.myMap$value + ")";
    }
  }
}
