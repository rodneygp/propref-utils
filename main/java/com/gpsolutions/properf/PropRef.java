package com.gpsolutions.properf;

import org.eclipse.jdt.annotation.NonNull;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PropRef {
    private static final String IS = "is";
    private static final String GET = "get";

    private static final ConcurrentHashMap<String, Object> ANNOTATIONS_CACHE = new ConcurrentHashMap<>();

    private static final Object EMPTY_ANNOTATION = "EMPTY";

    private PropRef() {
    }

    /**
     * Sonar: we intentionally use $ to attract developer's attention to unusual nature of this method.
     */
    public static <B> PropertyReference<B, B> $(final Class<B> firstEntityType) { // NOSONAR
        return new PropertyReference<>(null, null, firstEntityType);
    }

    /**
     * Sonar: we intentionally use $ to attract developer's attention to unusual nature of this method.
     */
    public static <B, P> PropertyReference<B, P> $(final PropertyNameGetter<B, P> getter) { // NOSONAR
        return new PropertyReference<>(null, getter);
    }

    /**
     * Sonar: we intentionally use $ to attract developer's attention to unusual nature of this method.
     */
    @SuppressWarnings("unchecked")
    public static <B, P> PropertyReference<B, P> $(final Member javaMember) { // NOSONAR
        if (javaMember instanceof Field) {
            return (PropertyReference<B, P>) $(javaMember.getDeclaringClass()).$((Field) javaMember); // NOSONAR
        }
        if (javaMember instanceof Method) {
            final Method method = (Method) javaMember;
            String methodName = method.getName();

            if (methodName.startsWith("set")) {
                methodName = "g" + methodName.substring(1);
            }
            final String propertyName = getPropertyNameByGetterName(methodName);
            final Class<P> returnType = (Class<P>) method.getReturnType();
            final PropertyReference<B, ?> parent = $((Class<B>) javaMember.getDeclaringClass());
            return new PropertyReference<>(parent, propertyName, returnType);
        }
        throw new PropRefException("Do not know hat to do with '" + javaMember + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <F, E, C extends Collection<E>, N> PropertyReference<F, N> forEach(final PropertyReference<F, C> prop,
                                                                                     final PropertyNameGetter<E, N> getter) {
        return new PropertyReference(prop, getter);
    }

    public static <F> void doWithProperties(final Class<F> claxx, final Consumer<PropertyReference<F, ?>> consumer) {
        ReflectionUtils.getAllFields(claxx).forEach(field -> {
            final PropertyReference<F, ?> pr = PropRef.$(claxx).$(field);
            consumer.accept(pr);
        });
    }

    public static <F> void doWithDeclaredProperties(final Class<F> claxx,
                                                    final Consumer<PropertyReference<F, ?>> consumer) {
        ReflectionUtils.getAllDeclaredFields(claxx).forEach(field -> {
            final PropertyReference<F, ?> pr = PropRef.$(claxx).$(field);
            consumer.accept(pr);
        });
    }

    public static <F, P> void doWithPropertyReferencesOfType(final Class<F> beanType,
                                                             final Consumer<PropertyReference<F, P>> consumer, final Class<P> propertyClass) {
        ReflectionUtils.getAllFields(beanType).forEach(field -> {
            if (propertyClass.isAssignableFrom(field.getType())) {
                final PropertyReference<F, P> pr = PropRef.$(beanType).$(field);
                consumer.accept(pr);
            }
        });
    }

    @FunctionalInterface
    public interface PropertyNameGetter<T, R> extends Function<T, R>, Serializable {
    }

    @FunctionalInterface
    public interface PropertySupplierGetter<R> extends Supplier<R>, Serializable {
    }

    @FunctionalInterface
    public interface PropertySupplierIntGetter extends Supplier<Integer>, Serializable {
    }

    @FunctionalInterface
    public interface PropertySupplierLongGetter extends Supplier<Long>, Serializable {
    }

    @NonNull
    static String getPropertyNameByGetterName(@NonNull final String getterMethodName) {
        if (getterMethodName.startsWith(GET) && getterMethodName.length() > GET.length()) {
            return Character.toLowerCase(getterMethodName.charAt(GET.length()))
                    + getterMethodName.substring(GET.length() + 1);
        } else if (getterMethodName.startsWith(IS) && getterMethodName.length() > IS.length()) {
            return Character.toLowerCase(getterMethodName.charAt(IS.length()))
                    + getterMethodName.substring(IS.length() + 1);
        } else {
            throw new PropRefException("Unable to identify property name: getter method name '" + getterMethodName
                    + "' doesn't obay conventions: must start with 'get' or 'is'.");
        }
    }

    public static final class PropertyReference<F, P> {

        private static final String PROBLEM_ACCESSING_PROPERTY = "Problem accessing property";
        private PropertyReference<F, ?> parent;
        private final String propName;
        private Class<P> propertyType;
        private boolean hasField = true;
        private Field cachedField;
        private boolean hasGetter = true;
        private Method cachedGetter;
        private boolean hasSetter = true;
        private Method cachedSetter;
        private boolean onlySearchForPublicMethods = false;

        private PropertyReference(final PropertyReference<F, ?> parent, final PropertyReference<F, P> other) {
            super();
            this.parent = parent;
            propName = other.propName;
            propertyType = other.propertyType;
            hasField = other.hasField;
            cachedField = other.cachedField;
            hasGetter = other.hasGetter;
            cachedGetter = other.cachedGetter;
            hasSetter = other.hasSetter;
            cachedSetter = other.cachedSetter;
            onlySearchForPublicMethods = other.onlySearchForPublicMethods;
        }

        private PropertyReference(final PropertyReference<F, ?> parent, final String propName,
                                  final Class<P> propertyType) {
            this.parent = parent;
            this.propName = propName;
            this.propertyType = propertyType;
        }

        public <N extends F> PropertyReference<N, P> castBaseType(final Class<N> castTo) {
            return new PropertyReference<>(PropRef.$(castTo), propName, propertyType);
        }

        @SuppressWarnings("unchecked")
        private <X> PropertyReference(final PropertyReference<F, X> parent, final PropertyNameGetter<X, P> getter) {
            final LambdaDescriptor serializedLambda = getSerializedLambda(getter);

            propName = serializedLambda.getPropertyName();
            final String implClass = serializedLambda.getImplClass().replace('/', '.');
            try {
                final ClassLoader classLoader = getClass().getClassLoader();
                final Class<?> loadClass = classLoader.loadClass(implClass);
                if (parent != null) {
                    this.parent = parent;
                } else {
                    this.parent = new PropertyReference<>(null, null, loadClass);
                }
                final String getterMethod = serializedLambda.getImplMethodName();
                final Method method = loadClass.getMethod(getterMethod);
                propertyType = (Class<P>) method.getReturnType();
            } catch (final ClassNotFoundException | NoSuchMethodException | SecurityException e) {
                throw new PropRefException("Problem identifying property class type for property '" + propName + "'");
            }
        }

        /**
         * Sonar: we intentionally use $ to attract developer's attention to unusual nature of this method.
         */
        public <N> PropertyReference<F, N> $(final PropertyNameGetter<P, N> getter) { // NOSONAR
            return new PropertyReference<>(this, getter);
        }

        public <O, N> PropertyReference<F, N> importForeignProperty(
                final PropertyReference<O, N> foreignPropertyReference) {
            return new PropertyReference<>(this, foreignPropertyReference.getPropertyName(),
                    foreignPropertyReference.getPropertyType());
        }

        /**
         * Sonar: we intentionally use $ to attract developer's attention to unusual nature of this method.
         */
        @SuppressWarnings("unchecked")
        public <N> PropertyReference<F, N> $(final Field propertyField) { // NOSONAR
            if (!propertyField.getDeclaringClass().isAssignableFrom(propertyType)) {
                throw new PropRefException(
                        "There is no such field '" + propertyField.getName() + "' in '" + propertyType.getName() + "'");
            }
            final PropertyReference<F, N> res =
                    new PropertyReference<>(this, propertyField.getName(), (Class<N>) propertyField.getType());
            res.hasField = true;
            res.cachedField = propertyField;
            return res;
        }

        private Field getPropertyField() {
            if (!hasField) {
                return null;
            }
            if (cachedField != null) {
                return cachedField;
            }
            // we have to find the field and cache it:
            if (parent == null) {
                hasField = false;
                return null;
            }
            Class<?> type = parent.getPropertyType();
            while (type != null && type != Object.class) {
                try {
                    cachedField = type.getDeclaredField(propName);
                    hasField = true;
                    return cachedField;
                } catch (final NoSuchFieldException e) {
                    // ignore, keep looking at parent
                }
                type = type.getSuperclass();
            }
            // filed not found:
            hasField = false;
            return null;
        }

        private Method getPropertyGetter() {
            if (!hasGetter) {
                return null;
            }
            if (cachedGetter != null) {
                return cachedGetter;
            }
            // we have to find the field and cache it:
            if (parent == null) {
                hasGetter = false;
                return null;
            }
            final Class<?> type = parent.getPropertyType();
            try {
                cachedGetter = type.getMethod(getPropertyGetterMethodName());
                hasGetter = true;
                return cachedGetter;
            } catch (final NoSuchMethodException e) {
                if (isOnlySearchForPublicMethods()) {
                    hasGetter = false;
                    return null;
                }
                // try with declared method:
                try {
                    cachedGetter = type.getDeclaredMethod(getPropertyGetterMethodName());
                    cachedGetter.setAccessible(true);
                    return cachedGetter;
                } catch (final NoSuchMethodException e1) {
                    hasGetter = false;
                    return null;
                }
            }
        }

        public String getPropertyGetterMethodName() {
            return ((boolean.class == getPropertyType()) ? IS : GET) + Character.toUpperCase(propName.charAt(0))
                    + propName.substring(1);
        }

        public String getPropertySetterMethodName() {
            return "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        }

        private Method getPropertySetter() {
            synchronized (this) {
                if (!hasSetter) {
                    return null;
                }
                if (cachedSetter != null) {
                    return cachedSetter;
                }
                // we have to find the field and cache it:
                if (parent == null) {
                    hasSetter = false;
                    return null;
                }
                final Class<?> type = parent.getPropertyType();
                final String setterName = "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
                hasSetter = false;
                try {
                    cachedSetter = type.getMethod(setterName, propertyType);
                } catch (final NoSuchMethodException e) {
                    if (isOnlySearchForPublicMethods()) {
                        return null;
                    }
                    // try with declared method:
                    try {
                        cachedSetter = type.getDeclaredMethod(setterName, propertyType);
                        cachedSetter.setAccessible(true);
                    } catch (final NoSuchMethodException e1) {
                        return null;
                    }
                }
                final Class<?> returnType = cachedSetter.getReturnType();
                if (returnType != void.class) {
                    throw new PropRefException("Invalid setter method: class: '" + type.getName() + "', method name: '"
                            + setterName + "'. Expected return type 'void', got '" + returnType.getName() + "'.");
                }
                hasSetter = true;
                return cachedSetter;
            }
        }

        public void toString(final StringBuilder sb) {
            if (parent != null && parent.propName != null) {
                parent.toString(sb);
                sb.append('.');
            }
            sb.append(propName);
        }

        public String getFullyQualifiedName() {
            return getFullyQualifiedName(new StringBuilder()).toString();
        }

        public StringBuilder getFullyQualifiedName(final StringBuilder sb) {
            if (parent != null) {
                if (parent.propName != null) {
                    parent.getFullyQualifiedName(sb);
                    sb.append('.');
                } else {
                    sb.append(parent.getPropertyType().getName());
                    sb.append('/');
                }
            }
            sb.append(propName);
            return sb;
        }

        @Override
        public String toString() {
            final StringBuilder res = new StringBuilder();
            toString(res);
            return res.toString();
        }

        private static void assertPropertyRefLambda(final SerializedLambda serializedLambda) {
            if (serializedLambda.getImplMethodKind() != MethodHandleInfo.REF_invokeVirtual
                    && serializedLambda.getImplMethodKind() != MethodHandleInfo.REF_invokeInterface) {
                final String implKind = MethodHandleInfo.referenceKindToString(serializedLambda.getImplMethodKind());
                throw new PropRefException("You can only use :: operator lambdas to get property name. "
                        + "Expected 'invokeVirtual' or 'invokeInterface' method, got: '" + implKind + "'");
            }
        }

        private static final Map<Class<? extends Serializable>, LambdaDescriptor> SERIALIZED_STATEFUL_LAMBDA_CACHE =
                Collections.synchronizedMap(new IdentityHashMap<>());

        static LambdaDescriptor getSerializedLambda(final Serializable lambda) {
            return SERIALIZED_STATEFUL_LAMBDA_CACHE.computeIfAbsent(lambda.getClass(), sClass -> {
                final SerializedLambda res;
                try {
                    final Method method = sClass.getDeclaredMethod("writeReplace");
                    method.setAccessible(true);
                    res = (SerializedLambda) method.invoke(lambda);
                } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                        | IllegalArgumentException | InvocationTargetException e) {
                    throw new PropRefException("Problem identifying property name.", e);
                }
                assertPropertyRefLambda(res);
                return new LambdaDescriptor(res);
            });
        }

        public static final class LambdaDescriptor {
            private final String implMethodName;
            private final String implClass;
            private final String instantiatedMethodType;
            private final String methodReturnType;
            @NonNull
            private final String propertyName;

            public LambdaDescriptor(final SerializedLambda sl) {
                implMethodName = sl.getImplMethodName();
                implClass = sl.getImplClass().replace('/', '.');
                instantiatedMethodType = sl.getInstantiatedMethodType();
                propertyName = getPropertyNameByGetterName(implMethodName);
                // instantiated method type looks like:
                // "..)Ljava/lang/Integer;"
                methodReturnType = instantiatedMethodType
                        .substring(instantiatedMethodType.indexOf(')') + 2, instantiatedMethodType.length() - 1)
                        .replace('/', '.');
            }

            public String getImplMethodName() {
                return implMethodName;
            }

            public String getImplClass() {
                return implClass;
            }

            public String getInstantiatedMethodType() {
                return instantiatedMethodType;
            }

            @NonNull
            public String getPropertyName() {
                return propertyName;
            }

            public String getMethodReturnType() {
                return methodReturnType;
            }
        }

        public Class<P> getPropertyType() {
            return propertyType;
        }

        @SuppressWarnings("unchecked")
        public <A extends Annotation> A getAnnotation(final Class<A> annotationClass) {
            final StringBuilder sb = new StringBuilder();
            getFullyQualifiedName(sb);
            sb.append(':').append(annotationClass.getName());
            final String key = sb.toString();

            final Object res = ANNOTATIONS_CACHE.computeIfAbsent(key, (k) -> {
                @SuppressWarnings("rawtypes") final Optional a = searchForAnnotation(annotationClass);
                return a.orElse(EMPTY_ANNOTATION);
            });

            if (res == EMPTY_ANNOTATION) {
                return null;
            } else {
                return (A) res;
            }
        }

        private <A extends Annotation> Optional<A> searchForAnnotation(final Class<A> annotationClass) {
            final Optional<A> result = searchForAnnotationAtGetter(annotationClass);
            if (result.isPresent()) {
                return result;
            }

            // fall back to field:
            return searchForAnnotationAtField(annotationClass);
        }

        private <A extends Annotation> Optional<A> searchForAnnotationAtField(final Class<A> annotationClass) {
            final A annotation;
            final Field fld = getPropertyField();
            if (fld != null) {
                annotation = fld.getAnnotation(annotationClass);
                if (annotation != null) {
                    return Optional.of(annotation);
                }
            }
            return Optional.empty();
        }

        private <A extends Annotation> Optional<A> searchForAnnotationAtGetter(final Class<A> annotationClass) {
            A annotation;
            final Method method = getPropertyGetter();
            if (method == null) {
                return Optional.empty();
            }
            annotation = method.getAnnotation(annotationClass);
            if (annotation != null) {
                return Optional.of(annotation);
            }
            // we must first look at superclasses:
            Class<?> cl = method.getDeclaringClass();

            while (cl != Object.class && cl != null) {
                cl = cl.getSuperclass();
                if (cl == Object.class || cl == null) {
                    break;
                }
                try {
                    final Method declaredMethod = cl.getDeclaredMethod(getPropertyGetterMethodName());
                    annotation = declaredMethod.getAnnotation(annotationClass);
                    if (annotation != null) {
                        return Optional.of(annotation);
                    }
                } catch (final NoSuchMethodException | SecurityException e) {
                    // ignore
                }
            }
            return Optional.empty();
        }

        public boolean hasAnnotation(final Class<? extends Annotation> annotationClass) {
            return getAnnotation(annotationClass) != null;
        }

        public <T extends Annotation> Optional<T> getOptionalAnnotation(final Class<T> annotationClass) {
            return Optional.ofNullable(getAnnotation(annotationClass));
        }

        public static <T extends Annotation> Optional<T> getOptionalAnnotation(final PropertyReference<?, ?> pr,
                                                                               final Class<T> annotationClass) {
            return Optional.ofNullable(pr.getAnnotation(annotationClass));
        }

        @SuppressWarnings("unchecked")
        public <T extends F> P getValueOf(final T of) {
            Objects.requireNonNull(of, "getValueOf argument is null");
            Object base = of;
            if (parent != null && parent.propName != null) {
                base = parent.getValueOf(of);
            }
            Objects.requireNonNull(base, "base of argument is null");
            final Method propertyGetter = getPropertyGetter();
            if (propertyGetter != null) {
                try {
                    return (P) propertyGetter.invoke(base);
                } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new PropRefException(PROBLEM_ACCESSING_PROPERTY + " '" + propName + "'.", e);
                }
            }
            throw new PropRefException("No property getter found for: '" + propName + "' in '"
                    + (parent == null ? "unknown" : parent.getPropertyType()) + "'");
        }

        public <T extends F> void setValueTo(final T to, final P newValue) {
            Object base = to;
            if (parent != null && parent.propName != null) {
                base = parent.getValueOf(to);
            }
            final Method propertySetter = getPropertySetter();
            if (propertySetter == null) {
                throw new PropRefException(
                        "No property setter found for: '" + getFullyQualifiedName() + "'");
            }
            try {
                propertySetter.invoke(base, newValue);
            } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new PropRefException(
                        PROBLEM_ACCESSING_PROPERTY + " '" + propName + "' for setting it. Value was of type: '"
                                + (newValue == null ? "null" : newValue.getClass().getName()) + "'",
                        e);
            }
        }

        @SuppressWarnings("unchecked")
        public Class<F> getRootType() {
            if (parent == null) {
                return (Class<F>) propertyType;
            }
            return parent.getRootType();
        }

        public String getPropertyName() {
            return propName;
        }

        @SuppressWarnings("unchecked")
        public Optional<P> getOptionalValueOf(final F of) {
            if (of == null) {
                return Optional.empty();
            }
            Optional<?> base = Optional.of(of);
            if (parent != null && parent.propName != null) {
                base = parent.getOptionalValueOf(of);
            }
            if (!base.isPresent()) {
                return Optional.empty();
            }
            final Method propertyGetter = getPropertyGetter();
            if (propertyGetter != null) {
                try {
                    return Optional.ofNullable((P) propertyGetter.invoke(base.get()));
                } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new PropRefException(PROBLEM_ACCESSING_PROPERTY + " '" + propName + "'.", e);
                }
            }
            throw new PropRefException("No property getter found for: '" + propName + "'");
        }

        private <N> PropertyReference<F, N> dot(final String property) {
            Method getterMethod;
            try {
                getterMethod =
                        propertyType.getMethod(GET + Character.toUpperCase(property.charAt(0)) + property.substring(1));
            } catch (final NoSuchMethodException | SecurityException e) {
                try {
                    getterMethod = propertyType
                            .getMethod(IS + Character.toUpperCase(property.charAt(0)) + property.substring(1));
                } catch (final NoSuchMethodException | SecurityException e1) {
                    throw new PropRefException("Can not find corresponding getter/is method for property '" + property
                            + "' in class '" + propertyType + "'");
                }
            }

            @SuppressWarnings("unchecked") final Class<N> newPropertyType = (Class<N>) getterMethod.getReturnType();
            return new PropertyReference<>(this, property, newPropertyType);
        }

        public boolean isOnlySearchForPublicMethods() {
            return !onlySearchForPublicMethods;

        }

        public void setOnlySearchForPublicMethods(final boolean onlySearchForPublicMethods) {
            this.onlySearchForPublicMethods = onlySearchForPublicMethods;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((parent == null) ? 0 : parent.hashCode());
            result = prime * result + ((propName == null) ? 0 : propName.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            @SuppressWarnings("unchecked") final PropertyReference<F, P> other = (PropertyReference<F, P>) obj;
            if (parent != null) {
                if (!parent.equals(other.parent)) {
                    return false;
                }
            } else {
                if (other.parent != null) {
                    return false;
                }
            }
            if (propName == null) {
                return other.propName == null;
            } else {
                return propName.equals(other.propName);
            }
        }

        @SuppressWarnings("unchecked")
        public <N> PropertyReference<F, N> rebase(final PropertyReference<? super P, N> reference) {
            final PropertyReference<? super P, N> refDeepCopy = reference.deepCopy();
            final PropertyReference<F, P> thisDeepCopy = deepCopy();
            @SuppressWarnings("rawtypes")
            PropertyReference refParent = refDeepCopy;
            while (refParent.parent.propName != null) {
                refParent = refParent.parent;
            }
            refParent.parent = thisDeepCopy;
            return (PropertyReference<F, N>) refDeepCopy;
        }

        private PropertyReference<F, P> deepCopy() {
            PropertyReference<F, ?> parentCopy = parent;
            if (parent != null) {
                parentCopy = parent.deepCopy();
            }
            return new PropertyReference<>(parentCopy, this);
        }

        @SuppressWarnings("unchecked")
        public void useSpecificPropertyType() {
            final Method propertyGetter = getPropertyGetter();
            if (propertyGetter != null) {
                propertyType = (Class<P>) propertyGetter.getReturnType();
            }
        }
    }

    public static <T, P> PropertyReference<T, P> parse(final Class<T> rootType, final String path) {
        @SuppressWarnings("unchecked")
        PropertyReference<T, P> result = (PropertyReference<T, P>) $(rootType);
        final String[] split = path.split("[.]");
        for (int i = 0; i < split.length; i++) {
            final String prName = split[i];
            result = result.dot(prName);
        }
        return result;
    }

    public static void resetAnnotationsCache() {
        ANNOTATIONS_CACHE.clear();
    }
}
