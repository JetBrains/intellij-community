package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;

/**
 * Used for lombok configuration to determine how to transform field names when turning them into accessor method names and vice versa.
 */
public enum CapitalizationStrategy {
	BASIC {
		@Override
    public String capitalize(String in) {
      return StringUtil.capitalize(in);
		}
	},
	BEANSPEC {
		@Override
    public String capitalize(String in) {
      return StringUtil.capitalizeWithJavaBeanConvention(in);
		}
	};

  public abstract String capitalize(String in);

	public static CapitalizationStrategy defaultValue() {
		return BASIC;
	}

  public static CapitalizationStrategy convertValue(String someValue) {
    for (CapitalizationStrategy enumValue : values()) {
      if (enumValue.name().equalsIgnoreCase(someValue) ) {
        return enumValue;
      }
    }
    return defaultValue();
	}
}
