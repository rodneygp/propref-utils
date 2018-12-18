package com.gpsolutions.properf;

import org.eclipse.jdt.annotation.NonNull;
import com.gpsolutions.properf.PropRef.PropertyReference;
import com.gpsolutions.properf.PropRef.PropertySupplierGetter;
import com.gpsolutions.properf.PropRef.PropertySupplierLongGetter;
import com.gpsolutions.properf.PropRef.PropertyNameGetter;
public final class PropName {
	private PropName() {}

	// we want this method to be specially named
	@NonNull
	public static <T> String $(final PropertySupplierGetter<T> getter) { // NOSONAR
		return PropertyReference.getSerializedLambda(getter).getPropertyName();
	}

	// we want this method to be specially named
	@NonNull
	public static <T> String $(final PropRef.PropertySupplierIntGetter getter) { // NOSONAR
		return PropertyReference.getSerializedLambda(getter).getPropertyName();
	}

	// we want this method to be specially named
	@NonNull
	public static <T> String $(final PropertySupplierLongGetter getter) { // NOSONAR
		return PropertyReference.getSerializedLambda(getter).getPropertyName();
	}

	// we want this method to be specially named
	@NonNull
	public static <B, P> String $(final PropertyNameGetter<B, P> getter) { // NOSONAR
		return PropertyReference.getSerializedLambda(getter).getPropertyName();
	}
}
