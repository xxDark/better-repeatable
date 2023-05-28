package dev.xdark.betterrepeatable;

import com.sun.codemodel.internal.JOp;

import javax.swing.*;
import java.lang.instrument.Instrumentation;

public final class InstrumentationAgent {

	public static final Object LOCK = new Object();
	public static volatile Instrumentation instrumentation;

	public static void agentmain(String args, Instrumentation instrumentation) {
		synchronized (InstrumentationAgent.LOCK) {
			InstrumentationAgent.instrumentation = instrumentation;
			InstrumentationAgent.LOCK.notifyAll();
		}
	}
}