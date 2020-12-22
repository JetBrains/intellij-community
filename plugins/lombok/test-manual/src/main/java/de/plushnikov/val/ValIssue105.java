package de.plushnikov.val;

import lombok.val;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ValIssue105 {
  private static class AccountUserEntity {

  }

  private static class User {
    public boolean isLegacyUser() {
      return true;
    }
  }

  private static class SomeMapper {
    public User map(AccountUserEntity entity, Class clazz) throws IllegalAccessException, InstantiationException {
      return new User();
    }
  }

  private SomeMapper mapper = new SomeMapper();

  @Test
  public void isLegacy_mapObjectWithDTO_returnFalse() throws Exception {
    val entity = new AccountUserEntity(); // <-- this row gives an "Incopatible types" asking for a User

    val dto = mapper.map(entity, User.class); // <-- while this row gives an "Incopatible types" asking for a AccountUserEntity

    assertThat(dto.isLegacyUser(), is(false));
  }
}
