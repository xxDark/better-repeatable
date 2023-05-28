package dev.xdark.betterrepeatable;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

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
				throw new UnsupportedOperationException("Not yet supported on Java 9+, internals changed");
			} catch (ClassNotFoundException ignored) {
				dev.xdark.betterrepeatable.java8.Initializer.init();
				initialized = true;
			}
		}
	}
}
