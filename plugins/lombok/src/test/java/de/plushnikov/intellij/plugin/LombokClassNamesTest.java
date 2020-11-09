package de.plushnikov.intellij.plugin;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;

import java.lang.reflect.Field;

public class LombokClassNamesTest extends AbstractLombokLightCodeInsightTestCase {
  public void testAllNames() throws IllegalAccessException {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(getProject());
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(getProject());
    for (Field field : LombokClassNames.class.getDeclaredFields()) {
      if (String.class.equals(field.getType())) {
        String name = (String)field.get(null);
        assertNotNull(name, psiFacade.findClass(name, allScope));
      }
    }
  }
}
