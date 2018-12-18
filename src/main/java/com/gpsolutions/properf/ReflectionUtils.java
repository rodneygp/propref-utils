package com.gpsolutions.properf;

import org.apache.commons.text.WordUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ReflectionUtils {

	private ReflectionUtils() {}

	public static List<Field> getAllFields(final Class<?> clazz) {
		final ArrayList<Field> res = new ArrayList<>();
		Class<?> targetClass = clazz;
		do {
			final Field[] fields = targetClass.getDeclaredFields();
			final List<Field> fs = Arrays.asList(fields);
			Collections.reverse(fs);
			res.addAll(fs);
			targetClass = targetClass.getSuperclass();
		} while (targetClass != null && targetClass != Object.class);
		Collections.reverse(res);
		return res;
	}

	public static List<Field> getAllDeclaredFields(final Class<?> clazz) {
		return Arrays.asList(clazz.getDeclaredFields());
	}

	public static <T extends Annotation> T getFieldAnnotation(final Class<?> entityClass, final Field field,
			final Class<T> annotationClass) {
		String methodName = field.getName();
		methodName = WordUtils.capitalize(methodName);
		final Class<?> type = field.getType();
		if (type.equals(Boolean.class) || type.equals(boolean.class)) {
			methodName = "is" + methodName;
		} else {
			methodName = "get" + methodName;
		}
		Class<?> currentClass = entityClass;
		do {
			Method method;
			try {
				method = currentClass.getDeclaredMethod(methodName);
				if (type.equals(method.getReturnType())) {
					final T annotation = method.getAnnotation(annotationClass);
					if (annotation != null) {
						return annotation;
					}
				}
			} catch (NoSuchMethodException | SecurityException e) {
				// ignore, try superclass
			}
			currentClass = currentClass.getSuperclass();
		} while (currentClass != null && currentClass != Object.class);
		return field.getAnnotation(annotationClass);
	}

	public static boolean hasAnnotation(final Class<?> entityClass, final Field field,
			final Class<? extends Annotation> annotationClass) {
		return getFieldAnnotation(entityClass, field, annotationClass) != null;
	}

	public static <T> Optional<Constructor<T>> getConstructor(final Class<T> clas, final Class<?>... params) {
		try {
			return Optional.of(clas.getDeclaredConstructor(params));
		} catch (NoSuchMethodException | SecurityException e) {
			return Optional.empty();
		}
	}

	public static <T extends Annotation> Optional<T> getClassAnnotationRecursive(final Class<?> clazz,
			final Class<T> annotationClass) {
		Class<?> currentClass = clazz;
		do {
			final T annotation = currentClass.getAnnotation(annotationClass);
			if (annotation != null) {
				return Optional.of(annotation);
			}
			currentClass = currentClass.getSuperclass();
		} while (currentClass != null && currentClass != Object.class);
		return Optional.empty();
	}
}