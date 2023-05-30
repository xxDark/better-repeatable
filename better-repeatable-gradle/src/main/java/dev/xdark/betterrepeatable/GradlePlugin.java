package dev.xdark.betterrepeatable;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GradlePlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		File pluginPath = findPluginPath(project).toFile();
		ObjectFactory objects = project.getObjects();
		for (JavaCompile compileJava : project.getTasks().withType(JavaCompile.class)) {
			compileJava.doFirst("better-repeatable inject", new Action<Task>() {
				@Override
				public void execute(Task task) {
					JavaLanguageVersion jdk9Version = JavaLanguageVersion.of(9);
					CompileOptions compilerOptions = compileJava.getOptions();
					ConfigurableFileCollection newCollection = objects.fileCollection();
					FileCollection collection = compilerOptions.getAnnotationProcessorPath();
					if (collection != null) {
						newCollection = newCollection.from(collection);
					}
					compilerOptions.setAnnotationProcessorPath(newCollection.from(pluginPath));
					compilerOptions.getCompilerArgs().add("-Xplugin:BetterRepeatable");
					JavaCompiler compiler = compileJava.getJavaCompiler().getOrNull();
					boolean isNewJdk;
					if (compiler != null) {
						isNewJdk = compiler.getMetadata().getLanguageVersion().canCompileOrRun(9);
					} else {
						isNewJdk = JavaVersion.current().isJava9Compatible();
					}
					if (isNewJdk) {
						ForkOptions forkOptions = compilerOptions.getForkOptions();
						List<String> jvmArgs = forkOptions.getJvmArgs();
						if (jvmArgs == null) {
							jvmArgs = new ArrayList<>(2);
						}
						jvmArgs.add("-Djdk.attach.allowAttachSelf=true");
						// In light of recent events...
						jvmArgs.add("-XX:+EnableDynamicAgentLoading");
						forkOptions.setJvmArgs(jvmArgs);
						compilerOptions.setFork(true);
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
