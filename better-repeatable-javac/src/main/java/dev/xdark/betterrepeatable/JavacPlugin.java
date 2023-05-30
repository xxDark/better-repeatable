package dev.xdark.betterrepeatable;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

import java.lang.reflect.InvocationTargetException;

public final class JavacPlugin implements Plugin {
	private static final Object INIT_LOCK = new Object();
	private static volatile boolean initialized;

	@Override
	public String getName() {
		return "BetterRepeatable";
	}

	@Override
	public void init(JavacTask task, String... args) {
		synchronized (INIT_LOCK) {
			if (initialized) return;
			try {
				Class.forName("java.lang.Module");
				invokeInit("java11");
			} catch (ClassNotFoundException ignored) {
				invokeInit("java8");
				initialized = true;
			}
		}
	}

	private static void invokeInit(String pckg) {
		try {
			Class.forName("dev.xdark.betterrepeatable." + pckg + ".Initializer").getMethod("init")
					.invoke(null);
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException("Failed to initialize", e);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			throw new RuntimeException("Cannot find initializer", e);
		}
	}
}
