// "Use lombok @Setter for 'releaseDate'" "true"

import lombok.Setter;

import java.util.Date;

public class Foo {
    /**
     * -- SETTER --
     *  Sets The date.
     *  It's an instance field.
     *
     * @param The date
     * @param The value
     */
    @Setter
    private Date releaseDate;
  private int fieldWithoutSetter;

}