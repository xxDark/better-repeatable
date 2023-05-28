package dev.xdark.betterrepeatable;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.util.UUID;

public final class APBuildManagerListener implements BuildManagerListener {

	@Override
	public void beforeBuildProcessStarted(@NotNull Project project, @NotNull UUID sessionId) {
		if (JavaPsiFacade.getInstance(project).findPackage("dev.xdark.betterrepeatable") != null) {
			CompilerConfigurationImpl configuration = (CompilerConfigurationImpl) CompilerConfiguration.getInstance(project);
			ProcessorConfigProfile profile = configuration.getDefaultProcessorProfile();
			profile.setEnabled(true);
			String pluginClass = "dev.xdark.betterrepeatable.JavacPlugin";
			profile.addProcessor(pluginClass);
		}
	}
}
