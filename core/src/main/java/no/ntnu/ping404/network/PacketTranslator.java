package no.ntnu.ping404.network;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class PacketTranslator {

    private static final String PING404_PREFIX = "no.ntnu.ping404.network.packets.";
    private static final String FRAMEWORK_PREFIX = "no.creekcode.kryonet.packets.";
    private static final Object NO_CLASS = new Object();
    private static final Map<String, Object> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> INSTANCE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> TARGET_FIELDS_CACHE = new ConcurrentHashMap<>();

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
        if (sourceClass.isArray()) {
            return translateArray(value, sourcePrefix, targetPrefix, seen);
        }
        if (value instanceof Collection<?> collection) {
            return translateCollection(collection, sourcePrefix, targetPrefix, seen);
        }
        if (value instanceof Map<?, ?> map) {
            return translateMap(map, sourcePrefix, targetPrefix, seen);
        }

        if (!sourceName.startsWith(sourcePrefix)) {
            return value;
        }

        if (sourceClass.isEnum()) {
            return translateEnum((Enum<?>) value, targetPrefix + sourceName.substring(sourcePrefix.length()));
        }

        String targetName = targetPrefix + sourceName.substring(sourcePrefix.length());
        try {
            Class<?> targetClass = resolveClass(targetName);
            if (targetClass == null) {
                return value;
            }
            Object target = instantiate(targetClass);
            seen.put(value, target);
            copyFields(value, target, sourcePrefix, targetPrefix, seen);
            normalizeTimestampFields(sourceClass, target.getClass(), target, sourcePrefix, targetPrefix);
            return target;
        } catch (ClassNotFoundException e) {
            // Preserve compatibility for packet types that exist only on one side.
            return value;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to translate packet class from "
                    + sourceName + " to " + targetName
                    + " (sourcePrefix=" + sourcePrefix + ", targetPrefix=" + targetPrefix + ")", e);
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
        } catch (ClassNotFoundException e) {
            return value;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to translate enum "
                    + value.getClass().getName() + " to " + targetName, e);
        }
    }

    private static Object translateArray(Object value, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) {
        int length = Array.getLength(value);
        Class<?> componentType = value.getClass().getComponentType();
        Class<?> targetComponentType = componentType;
        String componentName = componentType.getName();
        if (componentName.startsWith(sourcePrefix)) {
            try {
                Class<?> resolved = resolveClass(targetPrefix + componentName.substring(sourcePrefix.length()));
                if (resolved != null) {
                    targetComponentType = resolved;
                }
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
        Map<Object, Object> translated = new LinkedHashMap<>();
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
        Constructor<?> constructor = CONSTRUCTOR_CACHE.computeIfAbsent(type, PacketTranslator::resolveConstructor);
        return constructor.newInstance();
    }

    private static void copyFields(Object source, Object target, String sourcePrefix, String targetPrefix, Map<Object, Object> seen) throws IllegalAccessException {
        for (Field sourceField : getInstanceFields(source.getClass())) {
            Object fieldValue = sourceField.get(source);
            Object translatedValue = translate(fieldValue, sourcePrefix, targetPrefix, seen);

            Field targetField = getTargetField(target.getClass(), sourceField.getName());
            if (targetField == null || Modifier.isStatic(targetField.getModifiers())) {
                continue;
            }
            targetField.set(target, translatedValue);
        }
    }

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        Object cached = CLASS_CACHE.get(className);
        if (cached == NO_CLASS) {
            return null;
        }
        if (cached instanceof Class<?> clazz) {
            return clazz;
        }
        try {
            Class<?> resolved = Class.forName(className);
            CLASS_CACHE.put(className, resolved);
            return resolved;
        } catch (ClassNotFoundException e) {
            CLASS_CACHE.put(className, NO_CLASS);
            throw e;
        }
    }

    private static Constructor<?> resolveConstructor(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (Exception e) {
            throw new IllegalStateException("No accessible no-args constructor for " + type.getName(), e);
        }
    }

    private static Field[] getInstanceFields(Class<?> type) {
        return INSTANCE_FIELDS_CACHE.computeIfAbsent(type, clazz -> {
            Field[] declared = clazz.getDeclaredFields();
            ArrayList<Field> fields = new ArrayList<>(declared.length);
            for (Field field : declared) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            return fields.toArray(new Field[0]);
        });
    }

    private static Field getTargetField(Class<?> targetClass, String fieldName) {
        Map<String, Field> fieldsByName = TARGET_FIELDS_CACHE.computeIfAbsent(targetClass, clazz -> {
            Map<String, Field> map = new ConcurrentHashMap<>();
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                map.put(field.getName(), field);
            }
            return map;
        });
        return fieldsByName.get(fieldName);
    }

    private static void normalizeTimestampFields(
            Class<?> sourceClass,
            Class<?> targetClass,
            Object target,
            String sourcePrefix,
            String targetPrefix) {
        String sourceName = sourceClass.getName();
        if (sourceName.equals(sourcePrefix + "Ping")) {
            convertLongField(targetClass, target, "timestamp", sourcePrefix, targetPrefix);
            return;
        }
        if (sourceName.equals(sourcePrefix + "Pong")) {
            convertLongField(targetClass, target, "originalTimestamp", sourcePrefix, targetPrefix);
            convertLongField(targetClass, target, "serverTimestamp", sourcePrefix, targetPrefix);
        }
    }

    private static void convertLongField(Class<?> targetClass, Object target, String fieldName, String sourcePrefix, String targetPrefix) {
        Field field = getTargetField(targetClass, fieldName);
        if (field == null || field.getType() != long.class) {
            return;
        }
        try {
            long raw = field.getLong(target);
            if (raw < 0L) {
                return;
            }
            if (PING404_PREFIX.equals(sourcePrefix) && FRAMEWORK_PREFIX.equals(targetPrefix)) {
                field.setLong(target, TimeUnit.MILLISECONDS.toNanos(raw));
            } else if (FRAMEWORK_PREFIX.equals(sourcePrefix) && PING404_PREFIX.equals(targetPrefix)) {
                field.setLong(target, TimeUnit.NANOSECONDS.toMillis(raw));
            }
        } catch (IllegalAccessException ignored) {
        }
    }
}
