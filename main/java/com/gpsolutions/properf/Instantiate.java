package com.gpsolutions.properf;

import org.eclipse.jdt.annotation.NonNull;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;

public abstract class Instantiate {
	private Instantiate() {}

	public static <T> T create(final T t, final Consumer<T> x) {
		x.accept(t);
		postConstruct(t, t.getClass());
		return t;
	}

	public static <T> T createWithoutValidation(@NonNull final T t, @NonNull final Consumer<T> x) {
		x.accept(t);
		return t;
	}

	@NonNull
	public static <T> T $(@NonNull final T t, final Consumer<T> x) { // NOSONAR
		x.accept(t);
		postConstruct(t, t.getClass());
		return t;
	}

	@FunctionalInterface
	public interface ConsumerWithIOException<T> {
		void accept(T t) throws IOException;
	}

	@NonNull
	public static <T> T $withIOException(@NonNull final T t, final ConsumerWithIOException<T> x) throws IOException { // NOSONAR
		x.accept(t);
		postConstruct(t, t.getClass());
		return t;
	}

	@NonNull
	public static <T> T $$(@NonNull final Class<T> tClass, final Consumer<T> x) {// NOSONAR
		final T t;
		try {
			final Constructor<T> constructor = tClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			t = constructor.newInstance();
		} catch (final InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException
				| IllegalArgumentException | InvocationTargetException e) {
			final InstantiationError ex =
					new InstantiationError("Unable to instantiate class '" + tClass.getName() + "'");
			ex.initCause(e);
			throw ex;
		}
		x.accept(t);
		postConstruct(t, tClass);
		return t;
	}

	public static <T> void postConstruct(@NonNull final T t, @NonNull final Class<?> class1) {
		final Method[] postConstructMethods = POST_CONSTRUCT_METHODS_BY_CLASS.computeIfAbsent(class1, cl -> {
			int amount = 0;
			for (final Method method : cl.getMethods()) {
				if (method.isAnnotationPresent(PostConstruct.class)) {
					if (method.getParameterCount() != 0) {
						throw new IllegalArgumentException("@PostConstruct method " + method + " shall have no arguments.");
					}
					amount++;
				}
			}
			if (amount == 0) {
				return NO_POST_CONSTRUCT;
			}
			final Method[] pcMethods = new Method[amount];
			amount = 0;
			for (final Method method : cl.getMethods()) {
				if (method.isAnnotationPresent(PostConstruct.class)) {
					pcMethods[amount++] = method;
				}
			}
			return pcMethods;
		});
		for (final Method method : postConstructMethods) {
			try {
				method.invoke(t);
			} catch (final IllegalAccessException | InvocationTargetException e) {
				throw new IllegalArgumentException("Error post constructing " + class1 + "", e);
			}
		}
	}

	private static final Method[] NO_POST_CONSTRUCT = {};

	private static final ConcurrentHashMap<Class<?>, Method[]> POST_CONSTRUCT_METHODS_BY_CLASS =
			new ConcurrentHashMap<>();
}
