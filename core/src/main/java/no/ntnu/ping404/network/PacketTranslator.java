package no.ntnu.ping404.network;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class PacketTranslator {

    private static final String PING404_PREFIX = "no.ntnu.ping404.network.packets.";
    private static final String FRAMEWORK_PREFIX = "no.ntnu.kryonet.packets.";

    private PacketTranslator() {}

    static Object toFramework(Object packet) {
        return translate(packet, PING404_PREFIX, FRAMEWORK_PREFIX, new IdentityHashMap<>());
    }

    static Object toLegacy(Object packet) {
        return translate(packet, FRAMEWORK_PREFIX, PING404_PREFIX, new IdentityHashMap<>());
    }

    private static Object translate(Object value, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) {
        if (value == null) {
            return null;
        }
        if (isSimpleValue(value)) {
            return value;
        }
        Object existing = seen.get(value);
        if (existing != null) {
            return existing;
        }

        Class<?> sourceClass = value.getClass();
        String sourceName = sourceClass.getName();
        if (!sourceName.startsWith(sourcePrefix)) {
            return value;
        }

        if (sourceClass.isEnum()) {
            return translateEnum((Enum<?>) value, targetPrefix + sourceName.substring(sourcePrefix.length()));
        }
        if (sourceClass.isArray()) {
            return translateArray(value, sourcePrefix, targetPrefix, seen);
        }
        if (value instanceof Collection<?> collection) {
            return translateCollection(collection, sourcePrefix, targetPrefix, seen);
        }
        if (value instanceof Map<?, ?> map) {
            return translateMap(map, sourcePrefix, targetPrefix, seen);
        }

        String targetName = targetPrefix + sourceName.substring(sourcePrefix.length());
        try {
            Class<?> targetClass = Class.forName(targetName);
            Object target = instantiate(targetClass);
            seen.put(value, target);
            copyFields(value, target, sourcePrefix, targetPrefix, seen);
            return target;
        } catch (Exception e) {
            return value;
        }
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object translateEnum(Enum<?> value, String targetName) {
        try {
            Class<?> targetClass = Class.forName(targetName);
            return Enum.valueOf((Class<? extends Enum>) targetClass, value.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static Object translateArray(Object value, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) {
        int length = Array.getLength(value);
        Class<?> componentType = value.getClass().getComponentType();
        Class<?> targetComponentType = componentType;
        String componentName = componentType.getName();
        if (componentName.startsWith(sourcePrefix)) {
            try {
                targetComponentType = Class.forName(targetPrefix + componentName.substring(sourcePrefix.length()));
            } catch (ClassNotFoundException ignored) {
                targetComponentType = componentType;
            }
        }
        Object translatedArray = Array.newInstance(targetComponentType, length);
        seen.put(value, translatedArray);
        for (int i = 0; i < length; i++) {
            Array.set(translatedArray, i, translate(Array.get(value, i), sourcePrefix, targetPrefix, seen));
        }
        return translatedArray;
    }

    private static Object translateCollection(Collection<?> collection, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) {
        Collection<Object> translated = instantiateCollection(collection);
        seen.put(collection, translated);
        for (Object item : collection) {
            translated.add(translate(item, sourcePrefix, targetPrefix, seen));
        }
        return translated;
    }

    private static Object translateMap(Map<?, ?> map, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) {
        Map<Object, Object> translated = new java.util.LinkedHashMap<>();
        seen.put(map, translated);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            translated.put(
                    translate(entry.getKey(), sourcePrefix, targetPrefix, seen),
                    translate(entry.getValue(), sourcePrefix, targetPrefix, seen));
        }
        return translated;
    }

    private static Collection<Object> instantiateCollection(Collection<?> collection) {
        try {
            @SuppressWarnings("unchecked")
            Collection<Object> instance = (Collection<Object>) instantiate(collection.getClass());
            return instance;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Object instantiate(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void copyFields(Object source, Object target, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) throws IllegalAccessException {
        Class<?> sourceClass = source.getClass();
        for (Field sourceField : sourceClass.getDeclaredFields()) {
            if (Modifier.isStatic(sourceField.getModifiers())) {
                continue;
            }
            sourceField.setAccessible(true);
            Object fieldValue = sourceField.get(source);
            Object translatedValue = translate(fieldValue, sourcePrefix, targetPrefix, seen);

            Field targetField;
            try {
                targetField = target.getClass().getDeclaredField(sourceField.getName());
            } catch (NoSuchFieldException e) {
                continue;
            }
            if (Modifier.isStatic(targetField.getModifiers())) {
                continue;
            }
            targetField.setAccessible(true);
            if (fieldValue instanceof Collection<?> collection && Collection.class.isAssignableFrom(targetField.getType())) {
                translatedValue = translateCollection(collection, sourcePrefix, targetPrefix, seen);
            } else if (fieldValue instanceof Map<?, ?> map && Map.class.isAssignableFrom(targetField.getType())) {
                translatedValue = translateMap(map, sourcePrefix, targetPrefix, seen);
            }
            targetField.set(target, translatedValue);
        }
    }
}
