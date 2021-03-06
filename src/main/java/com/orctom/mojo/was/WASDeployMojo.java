package com.orctom.mojo.was;

import com.orctom.mojo.was.model.WebSphereModel;
import com.orctom.mojo.was.model.WebSphereServiceException;
import com.orctom.mojo.was.service.impl.WebSphereServiceScriptImpl;
import com.orctom.mojo.was.utils.AntTaskUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;

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
    protected String parallel;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info(Constants.PLUGIN_ID + " - deploy");
        Set<WebSphereModel> models = getWebSphereModels();

        if (null == models || models.isEmpty()) {
            getLog().info("[SKIPPED DEPLOYMENT] empty target server configured, please check your configuration");
            return;
        }

        boolean parallelDeploy = StringUtils.isEmpty(parallel) ? models.size() > 1 : Boolean.valueOf(parallel);

        if (parallelDeploy) {
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

        try {
            getLog().info("====================    pre-steps    =======================");
            executeAntTasks(model, super.preSteps);
            getLog().info("======================    deploy    ========================");
            new WebSphereServiceScriptImpl(model, project.getBuild().getDirectory()).deploy();
            getLog().info("====================    post-steps    ======================");
            executeAntTasks(model, super.postSteps);
        } catch (RuntimeException e) {
            if (failOnError) {
                throw e;
            } else {
                getLog().error("##############  Exception occurred during deploying to WebSphere  ###############");
                getLog().error(ExceptionUtils.getFullStackTrace(e));
            }
        } catch (Throwable t) {
            if (failOnError) {
                throw new WebSphereServiceException(t);
            } else {
                getLog().error("##############  Exception occurred during deploying to WebSphere  ###############");
                getLog().error(ExceptionUtils.getFullStackTrace(t));
            }
        }
    }

    private void executeAntTasks(WebSphereModel model, PlexusConfiguration[] targets) throws IOException, MojoExecutionException {
        if (null == targets || 0 == targets.length) {
            getLog().info("Skipped, not configured.");
            return;
        }
        for (PlexusConfiguration target : targets) {
            AntTaskUtils.execute(model, target, project, projectHelper, pluginArtifacts, getLog());
        }
    }

}
