package de.plushnikov.val;

import lombok.NoArgsConstructor;
import lombok.val;
import org.junit.Test;
import org.modelmapper.ModelMapper;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class Issue105 {
  private final ModelMapper mapper = new ModelMapper();

  @Test
  public void isLegacy_mapObjectWithDTO_returnFalse() throws Exception {
    val entity = new AccountUserEntity(); // <-- this row gives an "Incopatible types" asking for a User

    final User mapped = mapper.map(entity, User.class);

    val dto = mapper.map(entity, User.class); // <-- while this row gives an "Incopatible types" asking for a AccountUserEntity

    assertThat(dto.isLegacyUser(), is(false));
  }

  @NoArgsConstructor
  private static class AccountUserEntity {
  }

  @NoArgsConstructor
  private static class User {
    boolean isLegacyUser() {
      return false;
    }
  }
}
