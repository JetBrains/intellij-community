// "Use lombok @Getter for 'creationDate'" "true"

import lombok.Getter;

import java.util.Date;

public class MoveJavaDoc {
    /**
     * -- GETTER --
     *  Returns The date.
     *  It's an instance field.
     *
     * @return The date
     */
    @Getter
    private Date creationDate;
  private int fieldWithoutGetter;

}