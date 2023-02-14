package de.plushnikov.intellij.plugin;

import com.intellij.openapi.roots.OrderEntry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VersionTest {
  private OrderEntry orderEntry;

  @Before
  public void setUp() {
    orderEntry = mock(OrderEntry.class);
  }

  @Test
  public void parseLombokVersionFromGradle() {
    when(orderEntry.getPresentableName()).thenReturn("Gradle: org.projectlombok:lombok:1.16.8");
    assertEquals("1.16.8", Version.parseLombokVersion(orderEntry));
  }

  @Test
  public void parseLombokVersionFromMaven() {
    when(orderEntry.getPresentableName()).thenReturn("Maven: org.projectlombok:lombok:1.16.6");
    assertEquals("1.16.6", Version.parseLombokVersion(orderEntry));
  }

  @Test
  public void parseLombokVersionFromUnknown() {
    when(orderEntry.getPresentableName()).thenReturn("lombok");
    assertNull(Version.parseLombokVersion(orderEntry));
  }

  @Test
  public void isLessThan() {
    assertTrue(Version.isLessThan("1", "2"));
    assertTrue(Version.isLessThan("", "2"));
    assertFalse(Version.isLessThan("1.2.3", "1.2.1"));
    assertTrue(Version.isLessThan("1.16.6", "1.16.8"));
    assertFalse(Version.isLessThan("1.16.8", "1.16.8"));
    assertTrue(Version.isLessThan("0.10.2", "1.16.8"));
    assertFalse(Version.isLessThan("1.16.9", "1.16.8"));

    assertTrue(Version.isLessThan(null, Version.LAST_LOMBOK_VERSION));
    assertTrue(Version.isLessThan("", Version.LAST_LOMBOK_VERSION));
    assertFalse(Version.isLessThan("x", Version.LAST_LOMBOK_VERSION));
    assertFalse(Version.isLessThan("123", Version.LAST_LOMBOK_VERSION));

    assertFalse(Version.isLessThan(Version.LAST_LOMBOK_VERSION, null));
    assertFalse(Version.isLessThan(Version.LAST_LOMBOK_VERSION, ""));
    assertTrue(Version.isLessThan(Version.LAST_LOMBOK_VERSION, "x"));
    assertTrue(Version.isLessThan(Version.LAST_LOMBOK_VERSION, "123"));

    assertFalse(Version.isLessThan(Version.LAST_LOMBOK_VERSION, Version.LOMBOK_VERSION_WITH_JDK16_FIX));
    assertFalse(Version.isLessThan(Version.LAST_LOMBOK_VERSION, Version.LAST_LOMBOK_VERSION_WITH_JPS_FIX));
  }
}
