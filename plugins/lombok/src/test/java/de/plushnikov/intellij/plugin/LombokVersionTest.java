package de.plushnikov.intellij.plugin;

import com.intellij.openapi.roots.LombokVersion;
import com.intellij.openapi.roots.OrderEntry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LombokVersionTest {
  private OrderEntry orderEntry;

  @Before
  public void setUp() {
    orderEntry = mock(OrderEntry.class);
  }

  @Test
  public void parseLombokVersionFromGradle() {
    when(orderEntry.getPresentableName()).thenReturn("Gradle: org.projectlombok:lombok:1.16.8");
    assertEquals("1.16.8", LombokVersion.parseLombokVersion(orderEntry));
  }

  @Test
  public void parseLombokVersionFromMaven() {
    when(orderEntry.getPresentableName()).thenReturn("Maven: org.projectlombok:lombok:1.16.6");
    assertEquals("1.16.6", LombokVersion.parseLombokVersion(orderEntry));
  }

  @Test
  public void parseLombokVersionFromUnknown() {
    when(orderEntry.getPresentableName()).thenReturn("lombok");
    assertNull(LombokVersion.parseLombokVersion(orderEntry));
  }

  @Test
  public void isLessThan() {
    assertTrue(LombokVersion.isLessThan("1", "2"));
    assertTrue(LombokVersion.isLessThan("", "2"));
    assertFalse(LombokVersion.isLessThan("1.2.3", "1.2.1"));
    assertTrue(LombokVersion.isLessThan("1.16.6", "1.16.8"));
    assertFalse(LombokVersion.isLessThan("1.16.8", "1.16.8"));
    assertTrue(LombokVersion.isLessThan("0.10.2", "1.16.8"));
    assertFalse(LombokVersion.isLessThan("1.16.9", "1.16.8"));

    assertTrue(LombokVersion.isLessThan(null, LombokVersion.LAST_LOMBOK_VERSION));
    assertTrue(LombokVersion.isLessThan("", LombokVersion.LAST_LOMBOK_VERSION));
    assertFalse(LombokVersion.isLessThan("x", LombokVersion.LAST_LOMBOK_VERSION));
    assertFalse(LombokVersion.isLessThan("123", LombokVersion.LAST_LOMBOK_VERSION));

    assertFalse(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, null));
    assertFalse(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, ""));
    assertTrue(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, "x"));
    assertTrue(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, "123"));

    assertFalse(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, LombokVersion.LOMBOK_VERSION_WITH_JDK16_FIX));
    assertFalse(LombokVersion.isLessThan(LombokVersion.LAST_LOMBOK_VERSION, LombokVersion.LAST_LOMBOK_VERSION_WITH_JPS_FIX));
  }
}
