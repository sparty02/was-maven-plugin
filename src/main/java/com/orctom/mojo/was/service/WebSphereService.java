package com.orctom.mojo.was.service;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.ObjectNameHelper;
import com.ibm.websphere.management.Session;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;
import com.ibm.websphere.management.application.client.AppDeploymentTask;
import com.ibm.websphere.management.exception.ConnectorException;
import com.orctom.mojo.was.model.Server;
import com.orctom.mojo.was.model.WebSphereModel;
import com.orctom.mojo.was.model.WebSphereServiceException;
import org.apache.commons.lang.StringUtils;

import javax.management.*;
import java.util.*;

/**
 * Created by CH on 3/4/14.
 */
public class WebSphereService {

    private AdminClient client;
    private WebSphereModel model;

    public WebSphereService(WebSphereModel model) {
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public void refreshCluster() {
        if (StringUtils.isEmpty(model.getCluster())) {
            System.out.println("WARNING: Skipped due to NO cluster specified.");
            return;
        }
        try {
            ObjectName query = new ObjectName("WebSphere:*,type=Cluster,name=" + model.getCluster());
            Set<ObjectName> response = client.queryNames(query, null);
            for (ObjectName cluster : response) {
                client.invoke(cluster, "refresh", null, null);
            }
        } catch (Exception e) {
            throw new WebSphereServiceException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void restartCluster() {
        if (StringUtils.isEmpty(model.getCluster())) {
            System.out.println("WARNING: Skipped due to NO cluster specified.");
            return;
        }
        try {
            ObjectName query = new ObjectName("WebSphere:*,type=Cluster,name=" + model.getCluster());
            Set<ObjectName> response = client.queryNames(query, null);
            for (ObjectName cluster : response) {
                client.invoke(cluster, "rippleStart", null, null);
            }

        } catch (Exception e) {
            throw new WebSphereServiceException(e.getMessage(), e);
        }
    }

    public ObjectName getServer() throws MalformedObjectNameException, ConnectorException {
        ObjectName server = null;
        Session session = new Session();
        Set servers = null;
        boolean isCluster = this.isCluster();
        if (isCluster) {
            servers = client.queryNames(new ObjectName("WebSphere:type=Cluster,name=" + model.getCluster() + ",*"), null);
        } else {
            servers = client.queryNames(new ObjectName("WebSphere:type=Server,name=" + model.getServer() + ",*"), null);
        }
        if (servers.isEmpty()) {
            throw new WebSphereServiceException("IBM WebSphere server not found: " + (isCluster ? model.getCluster() : model.getServer()));
        }
        return (ObjectName) servers.iterator().next();
    }

    @SuppressWarnings("unchecked")
    public List<Server> listServers() {
        try {
            ObjectName query = new ObjectName("WebSphere:*,type=Server,j2eeType=J2EEServer");
            Set<ObjectName> response = client.queryNames(query, null);
            List<Server> servers = new ArrayList<Server>();
            for (ObjectName serverObjectName : response) {
                Server server = new Server();
                server.setCellName(String.valueOf(client.getAttribute(serverObjectName, "cellName")));
                server.setNodeName(String.valueOf(client.getAttribute(serverObjectName, "nodeName")));
                server.setServerName(String.valueOf(client.getAttribute(serverObjectName, "name")));
                server.setProcessId(String.valueOf(client.getAttribute(serverObjectName, "pid")));
                server.setServerVendor(String.valueOf(client.getAttribute(serverObjectName, "serverVendor")));
                server.setServerVersion(String.valueOf(client.getAttribute(serverObjectName, "serverVersion")));
                server.setState(String.valueOf(client.getAttribute(serverObjectName, "state")));
                servers.add(server);
            }
            return servers;
        } catch (Exception e) {
            throw new WebSphereServiceException(e.getMessage(), e);
        }
    }


    @SuppressWarnings("unchecked")
    public void restartServer() {
        if (StringUtils.isEmpty(model.getServer())) {
            System.out.println("WARNING: Skipped due to NO server specified.");
            return;
        }
        try {
            ObjectName query = new ObjectName("WebSphere:*,type=Server,j2eeType=J2EEServer,name=" + model.getServer());
            Set<ObjectName> response = client.queryNames(query, null);
            List<Server> servers = new ArrayList<Server>();
            for (ObjectName server : response) {
                client.invoke(server, "restart", null, null);
            }
        } catch (Exception e) {
            throw new WebSphereServiceException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Vector<String> listApplications() throws Exception {
        ApplicationListener listener = createInstallationListener();
        client.addNotificationListener(listener.getAppManagement(), listener, listener.getFilter(), "");
        return AppManagementProxy.getJMXProxyForClient(client).listApplications(new Hashtable(), null);
    }

    @SuppressWarnings("unchecked")
    public void installApplication() {
        try {
            Hashtable<String, Object> preferences = new Hashtable<String, Object>();
            preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

            Properties defaultBinding = new Properties();
            preferences.put(AppConstants.APPDEPL_DFLTBNDG, defaultBinding);
            if (StringUtils.isNotEmpty(model.getVirtualHost())) {
                defaultBinding.put(AppConstants.APPDEPL_DFLTBNDG_VHOST, model.getVirtualHost());
            }

            AppDeploymentController controller = AppDeploymentController.readArchive(model.getPackageFile().getAbsolutePath(), preferences);

            AppDeploymentTask task = controller.getFirstTask();
            while (task != null) {
                String[][] data = task.getTaskData();
                task.setTaskData(data);
                task = controller.getNextTask();
            }
            controller.saveAndClose();

            Hashtable<String, Object> config = controller.getAppDeploymentSavedResults();

            config.put(AppConstants.APPDEPL_DFLTBNDG, defaultBinding);

            config.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
            config.put(AppConstants.APPDEPL_APPNAME, model.getApplicationName());
            config.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
            config.put(AppConstants.APPDEPL_PRECOMPILE_JSP, Boolean.TRUE);

            //config.put( AppConstants.APPDEPL_CLASSLOADINGMODE, AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTLAST);
            //config.put( AppConstants.APPDEPL_CLASSLOADERPOLICY, AppConstants.APPDEPL_CLASSLOADERPOLICY_MULTIPLE);

            Hashtable<String, Object> module2server = new Hashtable<String, Object>();
            ObjectName server = getServer();
            if (this.isCluster()) {
                module2server.put("*", "WebSphere:cell=" + ObjectNameHelper.getCellName(server) + ",cluster=" + model.getCluster());
            } else {
                module2server.put("*", "WebSphere:cell=" + ObjectNameHelper.getCellName(server) + ",node="
                        + ObjectNameHelper.getNodeName(server) + ",server=" + model.getServer());
            }
            //module2server.put("*", getTarget());
            config.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);

            ApplicationListener listener = createInstallationListener();
            client.addNotificationListener(listener.getAppManagement(), listener, listener.getFilter(), "");
            AppManagementProxy.getJMXProxyForClient(client).installApplication(model.getPackageFile().getAbsolutePath(), config, null);
            waitForApplicationOperationsThread();
        } catch (Exception e) {
            throw new WebSphereServiceException("Failed to install application ", e);
        }
    }

    public void uninstallApplication() throws Exception {
        ApplicationListener listener = createInstallationListener();
        client.addNotificationListener(listener.getAppManagement(), listener, listener.getFilter(), "");
        AppManagementProxy.getJMXProxyForClient(client).uninstallApplication(model.getApplicationName(), new Hashtable(), null);
        waitForApplicationOperationsThread();
    }

    public void startApplication() throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(client).startApplication(model.getApplicationName(), new Hashtable(), null);
        } catch (Exception e) {
            throw new WebSphereServiceException("Could not start application '" + model.getApplicationName(), e);
        }
    }

    public void stopApplication() throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(client).stopApplication(model.getApplicationName(), new Hashtable(), null);
        } catch (Exception e) {
            throw new WebSphereServiceException("Could not stop application '" + model.getApplicationName(), e);
        }
    }

    public boolean isApplicationInstalled() {
        try {
            return AppManagementProxy.getJMXProxyForClient(client).checkIfAppExists(model.getApplicationName(), new Hashtable(), null);
        } catch (Exception e) {
            throw new WebSphereServiceException("Could not determine if application '" + model.getApplicationName() + "' is installed", e);
        }
    }

    private ApplicationListener createInstallationListener() throws Exception {
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(AppConstants.NotificationType);
        return new ApplicationListener(client, getAppManagementObject(), filter);
    }

    public boolean isConnected() {
        try {
            return null != client && null != client.isAlive();
        } catch (Exception e) {
            return false;
        }
    }

    public void connect() throws Exception {
        if (isConnected()) {
            System.out.println("WARNING: Already connected to WebSphere Application Server");
        }
        Properties config = new Properties();
        config.put(AdminClient.CONNECTOR_HOST, model.getHost());
        config.put(AdminClient.CONNECTOR_PORT, model.getPort());
        config.put(AdminClient.CACHE_DISABLED, Boolean.FALSE);
        if (StringUtils.isNotEmpty(model.getUser())) {
            injectSecurityConfiguration(config);
        } else {
            config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, Boolean.FALSE);
        }

        //config.put(AdminClient.AUTH_TARGET, getTarget());
        config.put(AdminClient.CONNECTOR_TYPE, model.getConnectorType());
        try {
            client = AdminClientFactory.createAdminClient(config);
        } catch (ConnectorException e) {
            throw new WebSphereServiceException("Unable to connect to IBM WebSphere Application Server, please check the firewall", e);
        }
        if (client == null) {
            throw new WebSphereServiceException("Unable to connect to IBM WebSphere Application Server @ " + model.getHost() + ":" + model.getPort());
        }
    }

    private boolean isCluster() {
        return StringUtils.isNotEmpty(model.getCluster());
    }

    private String getTarget() {
        StringBuilder builder = new StringBuilder();
        builder.append("WebSphere:");
        appendTarget(builder, "cluster=", model.getCluster());
        appendTarget(builder, "cell=", model.getCell());
        appendTarget(builder, "node=", model.getNode());
        appendTarget(builder, "server=", model.getServer());
        return builder.toString();
    }

    private void appendTarget(StringBuilder builder, String target, String value) {
        if (StringUtils.isNotEmpty(value)) {
            if (':' != builder.charAt(builder.length() - 1)) {
                builder.append(",");
            }
            builder.append(target);
            builder.append(value);
        }
    }

    private void injectSecurityConfiguration(Properties config) {
        config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, Boolean.TRUE);
        config.put(AdminClient.CONNECTOR_AUTO_ACCEPT_SIGNER, Boolean.TRUE);
        config.put(AdminClient.USERNAME, model.getUser());
        config.put(AdminClient.PASSWORD, model.getPassword());

        config.put("com.ibm.ssl.trustStore", model.getTrustStore().getAbsolutePath());
        config.put("javax.net.ssl.trustStore", model.getTrustStore().getAbsolutePath());

        config.put("com.ibm.ssl.keyStore", model.getKeyStore().getAbsolutePath());
        config.put("javax.net.ssl.keyStore", model.getKeyStore().getAbsolutePath());

        config.put("com.ibm.ssl.trustStorePassword", model.getTrustStorePassword());
        config.put("javax.net.ssl.trustStorePassword", model.getTrustStorePassword());

        config.put("com.ibm.ssl.keyStorePassword", model.getKeyStorePassword());
        config.put("javax.net.ssl.keyStorePassword", model.getKeyStorePassword());
    }

    public void disconnect() {
        client = null;
    }

    public boolean isAvailable() {
        try {
            Class.forName("com.ibm.websphere.management.AdminClientFactory", false, getClass().getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private ObjectName getAppManagementObject() throws MalformedObjectNameException, ConnectorException {
        //only one app management object exists for WebSphere so return the first one
        Iterator iterator = client.queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null).iterator();
        return (ObjectName) iterator.next();
    }

    private void waitForApplicationOperationsThread() {
        synchronized (this) {
            try {
                wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyApplicationOperationThread() {
        synchronized (this) {
            notify();
        }
    }

    class ApplicationListener implements NotificationListener {

        private AdminClient client;
        private ObjectName appManagement;
        private NotificationFilter filter;

        public ApplicationListener(AdminClient adminClient, ObjectName appManagement, NotificationFilter filter) {
            this.client = adminClient;
            this.appManagement = appManagement;
            this.filter = filter;
        }

        public synchronized void handleNotification(Notification notification, Object handback) {
            AppNotification ev = (AppNotification) notification.getUserData();

            System.out.println(ev.taskName + " <> " + ev.taskStatus);

            if ((ev.taskStatus.equals(AppNotification.STATUS_COMPLETED) || ev.taskStatus.equals(AppNotification.STATUS_FAILED))) {
                try {
                    client.removeNotificationListener(appManagement, this);
                } catch (Throwable th) {
                    System.err.println("Error removing install listener: " + th);
                }
                notifyApplicationOperationThread();
            }
        }

        public NotificationFilter getFilter() {
            return filter;
        }

        public AdminClient getClient() {
            return client;
        }

        public ObjectName getAppManagement() {
            return appManagement;
        }
    }

}