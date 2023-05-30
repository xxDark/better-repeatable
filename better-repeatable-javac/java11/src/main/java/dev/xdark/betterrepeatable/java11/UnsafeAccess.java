package dev.xdark.betterrepeatable.java11;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

final class UnsafeAccess {
	private UnsafeAccess() {
	}

	static Unsafe getUnsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe) f.get(null);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Cannot get theUnsafe", e);
		}
	}

	static MethodHandles.Lookup lookup() {
		Unsafe u = getUnsafe();
		try {
			Field f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			MethodHandles.publicLookup();
			return (MethodHandles.Lookup) u.getObject(u.staticFieldBase(f), u.staticFieldOffset(f));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Cannot get IMPL_LOOKUP", e);
		}
	}
}
