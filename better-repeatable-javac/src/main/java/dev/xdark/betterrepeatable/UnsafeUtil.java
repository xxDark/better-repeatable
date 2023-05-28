package dev.xdark.betterrepeatable;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public final class UnsafeUtil {
	private static final Unsafe UNSAFE;
	private static final MethodHandles.Lookup LOOKUP;

	private UnsafeUtil() {
	}

	public static Unsafe unsafe() {
		return UNSAFE;
	}

	public static MethodHandles.Lookup lookup() {
		return LOOKUP;
	}

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			Unsafe u = UNSAFE = (Unsafe) f.get(null);
			f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			MethodHandles.publicLookup();
			LOOKUP = (MethodHandles.Lookup) u.getObject(u.staticFieldBase(f), u.staticFieldOffset(f));
		} catch (ReflectiveOperationException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}
}
