package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLUtil {
  public static boolean isScalarValue(final PsiElement element) {
    if (element == null){
      return false;
    }
    //noinspection ConstantConditions
    final IElementType type = element.getNode().getElementType();
    return YAMLElementTypes.SCALAR_VALUES.contains(type) || type == YAMLTokenTypes.TEXT;
  }

  public static boolean isScalarOrEmptyCompoundValue(final PsiElement element) {
    return isScalarValue(element) || (element instanceof YAMLCompoundValue && ((YAMLCompoundValue)element).getYAMLElements().isEmpty());
  }

  @Nullable
  public static String getFullKey(final YAMLKeyValue yamlKeyValue) {
    final StringBuilder builder = new StringBuilder();
    YAMLKeyValue element = yamlKeyValue;
    PsiElement parent;
    while (element!=null &&
           (parent = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class, YAMLDocument.class)) instanceof YAMLKeyValue){
      if (builder.length()>0){
        builder.insert(0, '.');
      }
      builder.insert(0, element.getKeyText());
      element = (YAMLKeyValue) parent;
    }
    return builder.toString();
  }

  @Nullable
  public static YAMLPsiElement getRecord(final YAMLFile file, final String[] key) {
    assert key.length != 0;
    final YAMLPsiElement root = file.getDocuments().get(0);
    if (root != null){
      YAMLPsiElement record = root;
      for (int i=0;i<key.length;i++){
        record = findByName(record, key[i]);
        if (record == null){
          return null;
        }
      }
      return record;
    }
    return null;
  }

  @Nullable
  private static YAMLKeyValue findByName(final YAMLPsiElement element, final String name){
    final List<YAMLPsiElement> list;
    if (element instanceof YAMLKeyValue) {
      final PsiElement value = ((YAMLKeyValue)element).getValue();
      list = (value instanceof YAMLCompoundValue) ? ((YAMLCompoundValue)value).getYAMLElements() : Collections.<YAMLPsiElement>emptyList();
    } else {
      list = element.getYAMLElements();
    }
      for (YAMLPsiElement child : list) {
        if (child instanceof YAMLKeyValue){
          final YAMLKeyValue yamlKeyValue = (YAMLKeyValue)child;
          // We use null as wildcard
          if (name == null || name.equals(yamlKeyValue.getKeyText())){
            return yamlKeyValue;
          }
        }
      }
    return null;
  }

  @Nullable
  public static Pair<PsiElement, String> getValue(final YAMLFile file, final String[] key) {
    final YAMLPsiElement record = getRecord(file, key);
    if (record instanceof YAMLKeyValue) {
      final PsiElement psiValue = ((YAMLKeyValue)record).getValue();
      if (YAMLUtil.isScalarValue(psiValue)){
        return Pair.create(psiValue, ((YAMLKeyValue)record).getValueText());
      }
    }
    return null;
  }

  public List<String> getAllKeys(final YAMLFile file){
    return getAllKeys(file, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public List<String> getAllKeys(final YAMLFile file, final String[] key){
    final YAMLPsiElement record = getRecord(file, key);
    if (!(record instanceof YAMLKeyValue)){
      return Collections.emptyList();
    }
    PsiElement psiValue = ((YAMLKeyValue)record).getValue();

    final StringBuilder builder = new StringBuilder();
    for (String keyPart : key) {
      if (builder.length() != 0){
        builder.append(".");
      }
      builder.append(keyPart);
    }

    final ArrayList<String> list = new ArrayList<String>();

    addKeysRec(builder.toString(), psiValue, list);
    return list;
  }

  private static void addKeysRec(final String prefix, final PsiElement element, final List<String> list) {
    if (element instanceof YAMLCompoundValue){
      for (YAMLPsiElement child : ((YAMLCompoundValue)element).getYAMLElements()) {
        addKeysRec(prefix, child, list);
      }
    }
    if (element instanceof YAMLKeyValue){
      final YAMLKeyValue yamlKeyValue = (YAMLKeyValue)element;
      final PsiElement psiValue = yamlKeyValue.getValue();
      String key = yamlKeyValue.getKeyText();
      if (prefix.length() > 0){
        key = prefix + "." + key;
      }
      if (YAMLUtil.isScalarOrEmptyCompoundValue(psiValue)) {
        list.add(key);
      } else {
        addKeysRec(key, psiValue, list);
      }
    }
  }

  public YAMLKeyValue createI18nRecord(final YAMLFile file, final String key, final String text) {
    return createI18nRecord(file, key.split("\\."), text);
  }

  @Nullable
  public static YAMLKeyValue createI18nRecord(final YAMLFile file, final String[] key, final String text) {
    final YAMLPsiElement root = file.getDocuments().get(0);
    if (root != null){
      YAMLPsiElement record = root;
      final int keyLength = key.length;
      int i;
      for (i=0;i<keyLength;i++){
        final YAMLKeyValue nextRecord = findByName(record, key[i]);
        if (nextRecord != null){
          record = nextRecord;
        } else
        if (record instanceof YAMLKeyValue){
          final YAMLKeyValue keyValue = (YAMLKeyValue)record;
          final PsiElement value = keyValue.getValue();
          String indent = keyValue.getValueIndent();
          // Generate items
          final StringBuilder builder = new StringBuilder();
          builder.append("foo:");
          for (int j=i;j<keyLength;j++){
            builder.append("\n").append(indent.length() == 0 ? "  " : indent);
            builder.append(key[j]).append(":");
            indent += "  ";
          }
          builder.append(" ").append(text);
          final YAMLFile yamlFile =
            (YAMLFile) PsiFileFactory.getInstance(file.getProject())
              .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML,
                                  builder.toString(), LocalTimeCounter.currentTime(), true);
          final YAMLKeyValue topKeyValue = (YAMLKeyValue) yamlFile.getDocuments().get(0).getYAMLElements().get(0);
          final ASTNode generatedNode = topKeyValue.getNode();
          @SuppressWarnings({"ConstantConditions"})
          final ASTNode[] generatedChildren = generatedNode.getChildren(null);
          final ASTNode valueNode = value.getNode();
          if (valueNode instanceof LeafElement){
            return (YAMLKeyValue)value.replace(generatedChildren[3].getChildren(null)[0].getPsi());
          }
          //noinspection ConstantConditions
          valueNode.addChild(generatedChildren[1]);
          valueNode.addChild(generatedChildren[2]);
          valueNode.addChild(generatedChildren[3].getChildren(null)[0]);
          return (YAMLKeyValue) value.getLastChild();
        }
      }

      // Conflict with existing value
      final StringBuilder builder = new StringBuilder();
      final int top = Math.min(i + 1, keyLength);
      for (int j=0;j<top;j++){
        if (builder.length() > 0){
          builder.append('.');
        }
        builder.append(key[j]);
      }
      throw new IncorrectOperationException(YAMLBundle.message("new.name.conflicts.with", builder.toString()));
    }
    return null;
  }

  public static void removeI18nRecord(final YAMLFile file, final String[] key){
    PsiElement element = getRecord(file, key);
    while (element != null){
      final PsiElement parent = element.getParent();
      if (parent instanceof YAMLDocument) {
        ((YAMLKeyValue)element).getValue().delete();
        return;
      }
      if (parent instanceof YAMLCompoundValue) {
        if (((YAMLCompoundValue)parent).getYAMLElements().size() > 1) {
          element.delete();
          return;
        }
      }
      element = parent;
    }
  }

  public static PsiElement rename(final YAMLKeyValue element, final String newName) {
    if (newName.contains(".")){
      throw new IncorrectOperationException(YAMLBundle.message("rename.wrong.name"));
    }
    if (newName.equals(element.getName())){
      throw new IncorrectOperationException(YAMLBundle.message("rename.same.name"));
    }
    final YAMLFile yamlFile =
                (YAMLFile) PsiFileFactory.getInstance(element.getProject())
                  .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML,
                                      newName +": Foo", LocalTimeCounter.currentTime(), true);
    final YAMLKeyValue topKeyValue = (YAMLKeyValue) yamlFile.getDocuments().get(0).getYAMLElements().get(0);

    element.getKey().replace(topKeyValue.getKey());
    return element;
  }

}
