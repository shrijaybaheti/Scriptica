package com.scriptica.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScripticaLog {
	private static final int MAX_LINES = 200;
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final List<String> lines = new ArrayList<>();

	private ScripticaLog() {}

	public static void init() {
		append("[Scriptica] Log ready");
	}

	public static void info(String message) {
		append("[INFO " + LocalTime.now().format(TS) + "] " + message);
	}

	public static void error(String message) {
		append("[ERROR " + LocalTime.now().format(TS) + "] " + message);
	}

	public static void append(String message) {
		synchronized (lines) {
			lines.add(message);
			int overflow = lines.size() - MAX_LINES;
			if (overflow > 0) {
				lines.subList(0, overflow).clear();
			}
		}
	}

	public static List<String> snapshot() {
		synchronized (lines) {
			return Collections.unmodifiableList(new ArrayList<>(lines));
		}
	}
}

