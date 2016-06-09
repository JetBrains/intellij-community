package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigDiscoveryTest {

  private static final String EXPECTED_VALUE = "xyz";
  private ConfigDiscovery discovery;

  @Mock
  private FileBasedIndex fileBasedIndex;
  @Mock
  private GlobalSearchScope globalSearchScope;

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

  @Before
  public void setUp() throws Exception {
    discovery = new ConfigDiscovery(fileBasedIndex);

    when(project.getUserData(any(Key.class))).thenReturn(globalSearchScope);

    when(psiClass.getProject()).thenReturn(project);
    when(psiClass.getContainingFile()).thenReturn(psiFile);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
    when(virtualFile.getParent()).thenReturn(parentVirtualFile);

    when(virtualFile.getCanonicalPath()).thenReturn("/a/b/c/d/e/f/x.java");
    when(parentVirtualFile.getCanonicalPath()).thenReturn("/a/b/c/d/e/f");
  }

  @Test
  public void testDefaultStringConfigProperties() throws Exception {
    final String property = discovery.getStringLombokConfigProperty(ConfigKey.ACCESSORS_CHAIN, psiClass);
    assertNotNull(property);
    assertEquals(ConfigKey.ACCESSORS_CHAIN.getConfigDefaultValue(), property);
  }

  @Test
  public void testStringConfigPropertySameDirectory() throws Exception {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e/f", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList(EXPECTED_VALUE));

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(EXPECTED_VALUE, property);
  }

  @Test
  public void testStringConfigPropertySubDirectory() throws Exception {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList(EXPECTED_VALUE));

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(EXPECTED_VALUE, property);
  }

  @Test
  public void testStringConfigPropertySubDirectoryStopBubling() throws Exception {
    final ConfigKey configKey = ConfigKey.ACCESSORS_CHAIN;
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList(EXPECTED_VALUE));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e/f", ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("true"));

    final String property = discovery.getStringLombokConfigProperty(configKey, psiClass);
    assertNotNull(property);
    assertEquals(configKey.getConfigDefaultValue(), property);
  }

  @Test
  public void testMultipleStringConfigProperty() throws Exception {
    final ConfigKey configKey = ConfigKey.ACCESSORS_PREFIX;
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("+a;+b"));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("-a;+cc"));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.<String>emptyList());
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e/f", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("+_d;"));

    final String[] properties = discovery.getMultipleValueLombokConfigProperty(configKey, psiClass);
    assertNotNull(properties);
    assertEquals(3, properties.length);
    final ArrayList<String> list = new ArrayList<String>(Arrays.asList(properties));
    assertTrue(list.contains("b"));
    assertTrue(list.contains("cc"));
    assertTrue(list.contains("_d"));
  }

  @Test
  public void testMultipleStringConfigPropertyWithStopBubbling() throws Exception {
    final ConfigKey configKey = ConfigKey.ACCESSORS_PREFIX;
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("+a;+b"));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("-a;+cc"));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e", ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("true"));
    when(fileBasedIndex.getValues(LombokConfigIndex.NAME, new ConfigIndexKey("/a/b/c/d/e/f", configKey.getConfigKey()), globalSearchScope))
        .thenReturn(Collections.singletonList("+_d;"));

    final String[] properties = discovery.getMultipleValueLombokConfigProperty(configKey, psiClass);
    assertNotNull(properties);
    assertEquals(1, properties.length);
    final ArrayList<String> list = new ArrayList<String>(Arrays.asList(properties));
    assertTrue(list.contains("_d"));
  }
}