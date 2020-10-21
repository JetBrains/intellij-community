package de.plushnikov.intellij.plugin.activity;

import com.intellij.openapi.roots.OrderEntry;
import de.plushnikov.intellij.plugin.Version;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LombokProjectValidatorActivityTest {

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
  public void compareVersionString1_2() {
    assertEquals(-1, Version.compareVersionString("1", "2"));
  }

  @Test
  public void compareVersionString__2() {
    assertEquals(-1, Version.compareVersionString("", "2"));
  }

  @Test
  public void compareVersionString123_121() {
    assertEquals(1, Version.compareVersionString("1.2.3", "1.2.1"));
  }

  @Test
  public void compareVersionString1166_1168() {
    assertEquals(-1, Version.compareVersionString("1.16.6", "1.16.8"));
  }

  @Test
  public void compareVersionString1168_1168() {
    assertEquals(0, Version.compareVersionString("1.16.8", "1.16.8"));
  }

  @Test
  public void compareVersionString0102_1168() {
    assertEquals(-1, Version.compareVersionString("0.10.2", "1.16.8"));
  }

  @Test
  public void compareVersionString1169_1168() {
    assertEquals(1, Version.compareVersionString("1.16.9", "1.16.8"));
  }
}
