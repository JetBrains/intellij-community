package com.jetbrains.packagesearch.intellij.plugin.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptation from com.google.gson.internal.bind.TypeAdapters.EnumTypeAdapter
 * to overcome Gson assigning null values to enums.
 * <br/><br/>
 * Works around cases like <a href="https://discuss.kotlinlang.org/t/json-enum-deserialization-breakes-kotlin-null-safety/11670">https://discuss.kotlinlang.org/t/json-enum-deserialization-breakes-kotlin-null-safety/11670</a>.
*/
public class EnumWithDeserializationFallbackAdapterFactory implements TypeAdapterFactory {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (gson == null || type == null) {
            return null;
        }

        Class<T> rawType = (Class<T>) type.getRawType();
        if (!rawType.isEnum()) {
            return null;
        }

        return (TypeAdapter<T>) new EnumTypeAdapter(rawType);
    }

    private static final class EnumTypeAdapter<TT extends Enum<TT>> extends TypeAdapter<TT> {

        private TT defaultValue = null;
        private final Map<String, TT> nameToConstant = new HashMap<>();
        private final Map<TT, String> constantToName = new HashMap<>();

        EnumTypeAdapter(Class<TT> classOfT) {
            for (TT constant : classOfT.getEnumConstants()) {

                String name = constant.name();
                SerializedName serializedName;
                try {
                    serializedName = classOfT.getField(name).getAnnotation(SerializedName.class);
                } catch (NoSuchFieldException e) {
                    serializedName = null;
                }

                if (serializedName != null) {
                    name = serializedName.value();
                    for (String alternate : serializedName.alternate()) {
                        nameToConstant.put(alternate, constant);
                    }
                }

                // <adaptation>
                // First value annotated with DeserializationFallback becomes the default value
                if (defaultValue == null) {
                    DeserializationFallback fallbackAnnotation;
                    try {
                        fallbackAnnotation = classOfT.getField(name).getAnnotation(DeserializationFallback.class);
                    } catch (NoSuchFieldException e) {
                        fallbackAnnotation = null;
                    }

                    if (fallbackAnnotation != null) {
                        defaultValue = constant;
                    }
                }
                // </adaptation>

                nameToConstant.put(name, constant);
                constantToName.put(constant, name);
            }
        }

        @Override
        public TT read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            // <adaptation>
            // If null, return default value
            TT value = nameToConstant.get(in.nextString());
            if (value == null) {
                value = defaultValue;
            }
            // </adaptation>
            return value;
        }

        @Override
        public void write(JsonWriter out, TT value) throws IOException {
            out.value(value == null ? null : constantToName.get(value));
        }
    }
}