package com.scriptica.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScriptStorage {
	private ScriptStorage() {}

	public static void ensureDirs() {
		try {
			Files.createDirectories(ScripticaClientMod.getScriptsDir());
		} catch (IOException e) {
			ScripticaLog.error("Failed to create scripts dir: " + e.getMessage());
		}
	}

	public static List<String> listScriptNames() {
		Path dir = ScripticaClientMod.getScriptsDir();
		if (!Files.isDirectory(dir)) return Collections.emptyList();
		List<String> out = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.sca")) {
			for (Path path : ds) {
				String fileName = path.getFileName().toString();
				if (fileName.endsWith(".sca")) {
					out.add(fileName.substring(0, fileName.length() - 4));
				}
			}
		} catch (IOException e) {
			ScripticaLog.error("Failed to list scripts: " + e.getMessage());
		}
		out.sort(String::compareToIgnoreCase);
		return out;
	}

	public static String load(String name) throws IOException {
		Path path = resolveName(name);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	public static void save(String name, String source) throws IOException {
		Path path = resolveName(name);
		Files.createDirectories(path.getParent());
		Files.writeString(path, source == null ? "" : source, StandardCharsets.UTF_8);
	}

	public static void delete(String name) throws IOException {
		Path path = resolveName(name);
		Files.deleteIfExists(path);
	}

	private static Path resolveName(String name) {
		String safe = sanitizeName(name);
		return ScripticaClientMod.getScriptsDir().resolve(safe + ".sca");
	}

	public static String sanitizeName(String name) {
		if (name == null) return "untitled";
		String trimmed = name.trim();
		if (trimmed.isEmpty()) return "untitled";
		String safe = trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (safe.length() > 64) safe = safe.substring(0, 64);
		return safe;
	}
}

