package dev.xdark.betterrepeatable;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GradlePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		File pluginPath = findPluginPath(project).toFile();
		ObjectFactory objects = project.getObjects();
		JavaLanguageVersion jdk9Version = JavaLanguageVersion.of(9);
		for (JavaCompile compileJava : project.getTasks().withType(JavaCompile.class)) {
			compileJava.doFirst("better-repeatable inject", __ -> {
				CompileOptions options = compileJava.getOptions();
				ConfigurableFileCollection newCollection = objects.fileCollection();
				FileCollection collection = options.getAnnotationProcessorPath();
				if (collection != null) {
					newCollection = newCollection.from(collection);
				}
				options.setAnnotationProcessorPath(newCollection.from(pluginPath));
				JavaCompiler compiler = compileJava.getJavaCompiler().getOrNull();
				List<String> args = options.getCompilerArgs();
				args.add("-Xplugin:BetterRepeatable");
				if (compiler != null) {
					JavaLanguageVersion version = compiler.getMetadata().getLanguageVersion();
					if (version.canCompileOrRun(jdk9Version)) {
						args.add("-Djdk.attach.allowAttachSelf=true");
						// In light of recent events...
						args.add("-XX:+EnableDynamicAgentLoading");
						// TODO do we need to fork?
						options.setFork(true);
					}
				}
			});
		}
	}

	private static Path findPluginPath(Project project) {
		try {
			Object o = project.getExtensions().getByName("better_repeatable_plugin");
			if (o instanceof Path) {
				return (Path) o;
			} else if (o instanceof File) {
				return ((File) o).toPath();
			}
		} catch (UnknownDomainObjectException ignored) {
		}
		try {
			Class<?> c = Class.forName("dev.xdark.betterrepeatable.JavacPlugin");
			return Paths.get(c.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (ClassNotFoundException ignored) {
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot get path to plugin jar", e);
		}
		throw new IllegalStateException("Cannot find plugin path for project " + project);
	}
}
