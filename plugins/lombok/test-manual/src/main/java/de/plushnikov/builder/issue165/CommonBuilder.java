package de.plushnikov.builder.issue165;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

public class CommonBuilder {
  @Builder(builderMethodName = "personBuilder")
  public static Person create(String gender, String firstName, String lastName, List<String> children, boolean isParent) {
    Person person = new Person();
    person.setGender(gender);
    person.setFirstName(firstName);
    person.setLastName(lastName);
    person.setChildren(children);
    person.setParent(isParent);
    return person;
  }

  @Builder(builderMethodName = "petBuilder")
  public static Pet create(String species, String name) {
    Pet pet = new Pet();
    pet.setSpecies(species);
    pet.setName(name);
    return pet;
  }

  public static class PersonBuilder {
    public PersonBuilder withTwoKids() {
      children = new ArrayList<>();
      children.add("Jack");
      children.add("Jill");
      isParent = true;
      return this;
    }
  }

  public static class PetBuilder {
    public PetBuilder asMickeMouse() {
      species = "mouse";
      name = "Mickey Mouse";
      return this;
    }
  }
}