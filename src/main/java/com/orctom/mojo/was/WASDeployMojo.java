package com.orctom.mojo.was;

import com.orctom.mojo.was.model.WebSphereModel;
import com.orctom.mojo.was.service.WebSphereServiceFactory;
import com.orctom.mojo.was.utils.AntTaskUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * websphere deployment mojo
 * Created by CH on 3/4/14.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresDirectInvocation = true, threadSafe = true)
public class WASDeployMojo extends AbstractWASMojo {

    @Parameter
    protected boolean parallel;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info(Constants.PLUGIN_ID + " - deploy");
        Set<WebSphereModel> models = getWebSphereModels();

        if (models.isEmpty()) {
            getLog().info("[SKIPPED] empty target server configured, please check your configuration");
            return;
        }

        if (parallel) {
            ExecutorService executor = Executors.newFixedThreadPool(models.size());
            for (final WebSphereModel model : models) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        execute(model);
                    }
                });
            }
            executor.shutdown();

            try {
                executor.awaitTermination(20, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            for (WebSphereModel model : models) {
                execute(model);
            }
        }
    }

    private void execute(WebSphereModel model) {
        getLog().info("============================================================");
        getLog().info("[DEPLOY] " + model.getHost() + " " + model.getApplicationName());
        getLog().info("============================================================");
        executeAntTasks(model, super.preSteps);

        getLog().info("======================    deploy    ========================");
        WebSphereServiceFactory.getService(mode, model, project.getBuild().getOutputDirectory()).deploy();

        executeAntTasks(model, super.postSteps);
    }

    private void executeAntTasks(WebSphereModel model, PlexusConfiguration[] targets) {
        for (PlexusConfiguration target : targets) {
            try {
                AntTaskUtils.execute(model, target, project, projectHelper, pluginArtifacts, getLog());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MojoExecutionException e) {
                e.printStackTrace();
            }
        }
    }

}