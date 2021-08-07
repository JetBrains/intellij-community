package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigDiscoveryTest {

  private static final String EXPECTED_VALUE = "xyz";
  private ConfigDiscovery discovery;

  @Mock
  private FileBasedIndex fileBasedIndex;

  @Mock
  private MyProject project;
  @Mock
  private PsiClass psiClass;
  @Mock
  private PsiFile psiFile;
  @Mock
  private VirtualFile virtualFile;
  @Mock
  private VirtualFile parentVirtualFile;
  @Mock
  private VirtualFile parentParVirtualFile;
  @Mock
  private VirtualFile parentParParVirtualFile;
  @Mock
  private VirtualFile parentParParParVirtualFile;

  @Before
  public void setUp() {
    TestApplicationManager.getInstance();
    discovery = new ConfigDiscovery() {
      @Override
      protected FileBasedIndex getFileBasedIndex() {
        return fileBasedIndex;
      }

      @Override
      protected @NotNull Collection<String> discoverPropertyWithCache(@NotNull ConfigKey configKey,
                                                                      @NotNull PsiFile psiFile) {
        return super.discoverProperty(configKey, psiFile);
      }
    };

    when(psiFile.getProject()).thenReturn(project);
    when(psiClass.getContainingFile()).thenReturn(psiFile);
    when(psiFile.getOriginalFile()).thenReturn(psiFile);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
    when(virtualFile.getParent()).thenReturn(parentVirtualFile);
    when(parentVirtualFile.getParent()).thenReturn(parentParVirtualFile);
    when(parentParVirtualFile.getParent()).thenReturn(parentParParVirtualFile);
    when(parentParParVirtualFile.getParent()).thenReturn(parentParParParVirtualFile);
  }

  @Test
  public void testDefaultStringConfigProperties() {
    final String property = discovery.getStringLombokConfigProperty(ConfigKey.ACCESSORS_CHAIN, psiClass);
    assertNotNull(property);
    assertEquals(ConfigKey.ACCESSORS_CHAIN.getConfigDefaultValue(), property);
  }

  @Test
  public void testStringConfigPropertySameDirectory() {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;
    when(fileBasedIndex.getValues(eq(LombokConfigIndex.NAME), eq(configKey), isA(GlobalSearchScope.class)))
      .thenReturn(makeValue(EXPECTED_VALUE));

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(EXPECTED_VALUE, property);
  }

  @Test
  public void testStringConfigPropertySubDirectory() {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;
    when(fileBasedIndex.getValues(eq(LombokConfigIndex.NAME), eq(configKey), isA(GlobalSearchScope.class)))
      .thenReturn(makeValue(EXPECTED_VALUE));

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(EXPECTED_VALUE, property);
  }

  @Test
  public void testStringConfigPropertySubDirectoryStopBubbling() {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(configKey.getConfigDefaultValue(), property);
  }

  @Test
  public void testMultipleStringConfigProperty() {
    final ConfigKey configKey = ConfigKey.ACCESSORS_PREFIX;
    when(fileBasedIndex.getValues(eq(LombokConfigIndex.NAME), eq(configKey), any(GlobalSearchScope.class)))
      .thenReturn(makeValue("+_d;"), Collections.emptyList(), makeValue("-a;+cc"), makeValue("+a;+b"));

    final Collection<String> properties = discovery.getMultipleValueLombokConfigProperty(configKey, psiClass);
    assertThat(properties, hasItems("b", "cc", "_d"));
  }

  @NotNull
  private static List<ConfigValue> makeValue(String value) {
    return Collections.singletonList(new ConfigValue(value, false));
  }

  @Test
  public void testMultipleStringConfigPropertyWithStopBubbling() {
    final ConfigKey configKey = ConfigKey.ACCESSORS_PREFIX;
    when(fileBasedIndex.getValues(eq(LombokConfigIndex.NAME), eq(configKey), isA(GlobalSearchScope.class)))
      .thenReturn(makeValue("+_d;"));

    final Collection<String> properties = discovery.getMultipleValueLombokConfigProperty(configKey, psiClass);
    assertThat(properties, hasItems("_d"));
  }
}
