import lombok.Builder;

@Builder
public record Person(String name) {
  public static PersonBuilder builder() {
    return new PersonBuilder();
  }

  public static class PersonBuilder {
    private String name;

    PersonBuilder() {
    }

    public PersonBuilder name(String name) {
      this.name = name;
      return this;
    }

    public Person build() {
      return new Person(name);
    }

    public String toString() {
      return "Person.PersonBuilder(name=" + this.name + ")";
    }
  }
}
