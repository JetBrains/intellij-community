// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

@State(name = "PropertiesSeparatorManager")
public final class PropertiesSeparatorManager implements PersistentStateComponent<PropertiesSeparatorManager.PropertiesSeparatorManagerState> {
  private final Project myProject;

  public static PropertiesSeparatorManager getInstance(final Project project) {
    return project.getService(PropertiesSeparatorManager.class);
  }

  private PropertiesSeparatorManagerState myUserDefinedSeparators = new PropertiesSeparatorManagerState();
  private final SoftFactoryMap<ResourceBundleImpl, String> myGuessedSeparators = new SoftFactoryMap<>() {
    @Override
    protected String create(@NotNull ResourceBundleImpl resourceBundle) {
      return guessSeparator(resourceBundle);
    }
  };

  public PropertiesSeparatorManager(final Project project) {
    myProject = project;
  }

  @NotNull
  public String getSeparator(final ResourceBundle resourceBundle) {
    if (!(resourceBundle instanceof ResourceBundleImpl resourceBundleImpl)) {
      return ".";
    }
    String separator = myUserDefinedSeparators.getSeparators().get(resourceBundleImpl.getUrl());
    return separator == null ? Objects.requireNonNull(myGuessedSeparators.get(resourceBundleImpl)) : separator;
  }

  //returns most probable separator in properties files
  private static String guessSeparator(final ResourceBundleImpl resourceBundle) {
    final Int2LongOpenHashMap charCounts = new Int2LongOpenHashMap();
    for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      if (propertiesFile == null) continue;
      List<IProperty> properties = propertiesFile.getProperties();
      for (IProperty property : properties) {
        String key = property.getUnescapedKey();
        if (key == null) continue;
        for (int i =0; i<key.length(); i++) {
          char c = key.charAt(i);
          if (!Character.isLetterOrDigit(c)) {
            charCounts.put(c, charCounts.get(c) + 1);
          }
        }
      }
    }

    final char[] mostProbableChar = new char[]{'.'};
    charCounts.keySet().forEach(new IntConsumer() {
      long count = -1;
      @Override
      public void accept(int ch) {
        long charCount = charCounts.get(ch);
        if (charCount > count) {
          count = charCount;
          mostProbableChar[0] = (char)ch;
        }
      }
    });
    if (mostProbableChar[0] == 0) {
      mostProbableChar[0] = '.';
    }
    return Character.toString(mostProbableChar[0]);
  }

  public void setSeparator(ResourceBundle resourceBundle, String separator) {
    if (resourceBundle instanceof ResourceBundleImpl) {
      myUserDefinedSeparators.getSeparators().put(((ResourceBundleImpl)resourceBundle).getUrl(), separator);
    }
  }

  @Override
  public void loadState(@NotNull final PropertiesSeparatorManagerState state) {
    myUserDefinedSeparators = state.decode(myProject);
  }

  @Nullable
  @Override
  public PropertiesSeparatorManagerState getState() {
    return myUserDefinedSeparators.isEmpty() ? null : myUserDefinedSeparators.encode();
  }

  public static class PropertiesSeparatorManagerState {
    @Property(surroundWithTag = false)
    @XMap(keyAttributeName = "url", valueAttributeName = "separator", entryTagName = "file")
    public Map<String, String> mySeparators = new HashMap<>();

    public Map<String, String> getSeparators() {
      return mySeparators;
    }

    public boolean isEmpty() {
      return mySeparators.isEmpty();
    }

    public PropertiesSeparatorManagerState encode() {
      PropertiesSeparatorManagerState encodedState = new PropertiesSeparatorManagerState();
      for (final Map.Entry<String, String> entry : mySeparators.entrySet()) {
        String separator = entry.getValue();
        StringBuilder encoded = new StringBuilder(separator.length());
        for (int i=0;i<separator.length();i++) {
          char c = separator.charAt(i);
          encoded.append("\\u");
          encoded.append(String.format("%04x", (int) c));
        }
        encodedState.getSeparators().put(entry.getKey(), encoded.toString());
      }
      return encodedState;
    }

    public PropertiesSeparatorManagerState decode(final Project project) {
      PropertiesSeparatorManagerState decoded = new PropertiesSeparatorManagerState();
      for (final Map.Entry<String, String> entry : mySeparators.entrySet()) {
        String separator = entry.getValue();
        separator = decodeSeparator(separator);
        if (separator == null) {
          continue;
        }
        final String url = entry.getKey();
        ResourceBundle resourceBundle = PropertiesImplUtil.createByUrl(url, project);
        if (resourceBundle != null) {
          decoded.getSeparators().put(url, separator);
        }
      }
      return decoded;
    }
  }

  @Nullable
  private static String decodeSeparator(String separator) {
    if (separator.length() % 6 != 0) {
      return null;
    }
    StringBuilder result = new StringBuilder();
    int pos = 0;
    while (pos < separator.length()) {
      String encodedCharacter = separator.substring(pos, pos+6);
      if (!encodedCharacter.startsWith("\\u")) {
        return null;
      }
      char code = (char) Integer.parseInt(encodedCharacter.substring(2), 16);
      result.append(code);
      pos += 6;
    }
    return result.toString();
  }
}
