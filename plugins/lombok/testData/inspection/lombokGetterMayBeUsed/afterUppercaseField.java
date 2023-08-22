// "Use lombok @Getter for 'Bar'" "true"

import lombok.Getter;

import java.util.Date;

public class Foo {
    /**
     * -- GETTER --
     *  Returns The date.
     *
     * @return The date
     */
    @Getter
    private Date Bar;
  private int fieldWithoutGetter;

}