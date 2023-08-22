// "Use lombok @Setter for 'creationDate'" "true"

import lombok.Setter;

import java.util.Date;

public class MoveJavaDoc {
    /**
     * -- SETTER --
     *  Sets The date.
     *  It's an instance field.
     *
     * @param The date
     */
    @Setter
    private Date creationDate;
  private int fieldWithoutSetter;

}