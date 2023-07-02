// "Use lombok @Setter for 'Bar'" "true"

import lombok.Setter;

import java.util.Date;

public class Foo {
    /**
     * -- SETTER --
     *  Sets The date.
     *
     * @param The date
     */
    @Setter
    private Date Bar;
  private int fieldWithoutSetter;

}