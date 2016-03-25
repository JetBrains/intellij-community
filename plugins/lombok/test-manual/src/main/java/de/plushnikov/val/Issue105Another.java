package de.plushnikov.val;

import lombok.Data;
import lombok.val;
import org.junit.Test;
import org.modelmapper.ModelMapper;

import static org.hamcrest.CoreMatchers.is;

public class Issue105Another {

  private final ModelMapper mapper = new ModelMapper();

  @Test
  public void testLombokPlugin() throws Exception {
    val entity = new MyEntityClass();

    val dto = mapper.map(entity, MyDtoClass.class);

    assertThat(dto.isValue(), is(true));
  }

  @Data
  private class MyEntityClass {
    boolean value = true;
  }

  @Data
  private class MyDtoClass {
    boolean value;
  }
}