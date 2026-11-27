/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2025 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.api.util;

import lombok.NonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionUtil {
    private static MethodHandles.Lookup METHODHANDLE_LOOKUPER = MethodHandles.lookup();
    private static Field MODIFIERS_FIELD;
    private static Object THE_UNSAFE;
    private static MethodHandle PUT_OBJECT;
    private static MethodHandle STATIC_FIELD_OFFSET;

    static {
        MODIFIERS_FIELD = getField(Field.class, "modifiers");
    }

    /**
     * 使用 Class.forName() 通过全限定名加载类。
     *
     * @param name 全限定类名（例如 "java.util.ArrayList"）
     * @return 表示已加载类的 Class 对象
     * @throws RuntimeException 如果无法找到或加载该类
     */
    @SuppressWarnings("rawtypes")
    public static Class getClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            throw new RuntimeException("failed to load a class", e);
        }
    }

    /**
     * 从类中获取已声明的方法并使其可访问。
     * 该方法可以访问 private、protected 和包级私有方法。
     *
     * @param clazz 包含该方法的类
     * @param method 方法名
     * @param parameterTypes 方法的参数类型（可选，用于重载方法）
     * @return Method 对象；如果未找到该方法则返回 null
     */
    public static Method getMethod(Class<?> clazz, String method, Class<?>... parameterTypes) {
        try {
            Method m = clazz.getDeclaredMethod(method, parameterTypes);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 从类中获取已声明的字段并使其可访问。
     * 该方法可以访问 private、protected 和包级私有字段。
     *
     * @param clazz 包含该字段的类
     * @param field 字段名
     * @return Field 对象；如果未找到该字段则返回 null
     */
    public static Field getField(Class<?> clazz, String field) {
        try {
            Field f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 按字段名从目标对象中获取字段的值。
     * 该字段通过反射访问，可以是 private、protected 或包级私有。
     *
     * @param clazz 包含该字段的类
     * @param target 要从中获取字段值的对象实例（静态字段传 null）
     * @param field 字段名
     * @return 字段的值；如果未找到该字段或无法访问则返回 null
     */
    public static Object getFieldValue(Class<?> clazz, Object target, String field) {
        try {
            Field f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 使用 Field 对象从目标对象中获取字段的值。
     *
     * @param field 表示要访问字段的 Field 对象（不能为 null）
     * @param target 要从中获取字段值的对象实例（静态字段传 null）
     * @return 字段的值；如果无法访问该字段则返回 null
     */
    public static Object getFieldValue(@NonNull Field field, Object target) {
        try {
            return field.get(target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 使用 Field 对象设置目标对象上字段的值。
     *
     * @param field 表示要修改字段的 Field 对象（不能为 null）
     * @param target 要在其上设置字段值的对象实例（静态字段传 null）
     * @param value 要赋给该字段的新值
     * @return 如果字段设置成功则返回 true，否则返回 false
     */
    public static boolean setFieldValue(@NonNull Field field, Object target, Object value) {
        try {
            field.set(target, value);
            return true;
        } catch (Throwable e) {
            return false;
        }

    }

    /**
     * 按字段名设置目标对象上字段的值。
     * 该字段通过反射访问，可以是 private、protected 或包级私有。
     *
     * @param fieldName 要修改的字段名
     * @param target 要在其上设置字段值的对象实例（静态字段传 null）
     * @param value 要赋给该字段的新值
     * @return 如果字段设置成功则返回 true；如果未找到该字段或无法设置则返回 false
     */
    public static boolean setFieldValue(String fieldName, Object target, Object value) {
        try {
            Field field = getField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 按字段名设置目标对象上 final 字段的值。
     * 该方法会在赋值前移除 final 修饰符，从而允许修改声明为 final 的字段。
     * 请谨慎使用，因为这会破坏 Java 的不可变性保证。
     *
     * @param fieldName 要修改的 final 字段名
     * @param target 要在其上设置字段值的对象实例（静态字段传 null）
     * @param value 要赋给该字段的新值
     * @return 如果字段设置成功则返回 true；如果未找到该字段或无法设置则返回 false
     */
    public static boolean setFinalFieldValue(String fieldName, Object target, Object value) {
        try {
            Field field = getField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);

                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

                field.set(target, value);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 使用 Field 对象设置目标对象上 final 字段的值。
     * 在 Java 12 之前的版本中，该方法会在赋值前移除 final 修饰符。
     * 在 Java 12+ 中 modifiers 字段不可访问，但内部仍可通过 Unsafe API 修改 final 字段。
     *
     * @param field 表示要修改的 final 字段的 Field 对象（不能为 null）
     * @param target 要在其上设置字段值的对象实例（静态字段传 null）
     * @param value 要赋给该字段的新值
     * @return 如果字段设置成功则返回 true；如果无法设置则返回 false
     */
    public static boolean setFinalFieldValue(@NonNull Field field, Object target, Object value) {
        try {
            field.setAccessible(true);

            if(Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) < 12) { //Java 版本检查
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            }

            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 创建一个可设置 static final 字段值的 MethodHandle。
     * 修改 static final 字段时必须这样做，因为在较新的 Java 版本中无法通过
     * 常规反射修改它们。当 modifiers 字段不可访问时（Java 12+），该方法使用 Unsafe API。
     *
     * @param className 包含该 static final 字段的类
     * @param fieldName static final 字段的名称
     * @return 用于设置该字段的 MethodHandle；如果未找到该字段或无法访问则返回 null
     */
    public static MethodHandle createStaticFinalSetter(Class<?> className, String fieldName) {
        Field field = getField(className, fieldName);
        if (field == null) {
            return null;
        }

        if (MODIFIERS_FIELD == null) {
            if (THE_UNSAFE == null) {
                try {
                    THE_UNSAFE = getField(Class.forName("sun.misc.Unsafe"), "theUnsafe").get(null);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
                STATIC_FIELD_OFFSET = getMethodHandle(THE_UNSAFE.getClass(), "staticFieldOffset", Field.class).bindTo(THE_UNSAFE);
                PUT_OBJECT = getMethodHandle(THE_UNSAFE.getClass(), "putObject",  Object.class, long.class, Object.class).bindTo(THE_UNSAFE);
            }

            try {
                long offset = (long) STATIC_FIELD_OFFSET.invoke(field);

                return MethodHandles.insertArguments(PUT_OBJECT, 0, className, offset);
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }
        }

        try {
            MODIFIERS_FIELD.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return METHODHANDLE_LOOKUPER.unreflectSetter(field);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 为一个方法创建 MethodHandle，以便高效地重复调用。
     * 在重复调用时，MethodHandle 比标准反射性能更好。
     *
     * @param className 包含该方法的类
     * @param method 方法名
     * @param parameters 方法的参数类型（可选，用于重载方法）
     * @return 该方法的 MethodHandle；如果未找到该方法或 className 为 null 则返回 null
     */
    public static MethodHandle getMethodHandle(Class<?> className, String method, Class<?>... parameters) {
        if (className != null) {
            try {
                return METHODHANDLE_LOOKUPER.unreflect(getMethod(className, method, parameters));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 通过递归检查父类层级，判断一个类是否为另一个类的子类或与之相等。
     *
     * @param clazz 要检查的类
     * @param superClass 用于比较的候选父类
     * @return 如果 clazz 等于或继承自 superClass 则返回 true，否则返回 false
     */
    public static boolean isTypeOf(Class<?> clazz, Class<?> superClass) {
        if (!clazz.equals(superClass)) {
            clazz = clazz.getSuperclass();
            return !clazz.equals(Object.class) && isTypeOf(clazz, superClass);
        }
        return true;
    }
}
