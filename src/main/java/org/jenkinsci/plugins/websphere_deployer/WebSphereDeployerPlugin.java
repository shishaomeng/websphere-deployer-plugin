package org.jenkinsci.plugins.websphere_deployer;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.websphere_deployer.services.Deployable;
import org.jenkinsci.plugins.websphere_deployer.services.Endpoint;
import org.jenkinsci.plugins.websphere_deployer.services.J2EEApplication;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * A Jenkins plugin for deploying to WebSphere either locally or remotely.
 *
 * @author Greg Peters
 */
public class WebSphereDeployerPlugin extends Notifier {

    private final String ipAddress;
    private final String connectorType;
    private final String port;
    private final String username;
    private final String password;
    private final String clientKeyFile;
    private final String clientTrustFile;
    private final String node;
    private final String cell;
    private final String server;
    private final String artifacts;
    private final String clientKeyPassword;
    private final String clientTrustPassword;
    private final String appName;
    private final boolean autoStart;
    private final boolean precompile;
    private final boolean reloading;

    @DataBoundConstructor
    public WebSphereDeployerPlugin(String ipAddress,
                                   String connectorType,
                                   String port,
                                   String username,
                                   String password,
                                   String clientKeyFile,
                                   String clientTrustFile,
                                   String artifacts,
                                   String node,
                                   String cell,
                                   String server,
                                   String clientKeyPassword,
                                   String clientTrustPassword,
                                   String appName,
                                   boolean autoStart,
                                   boolean precompile,
                                   boolean reloading) {
        this.ipAddress = ipAddress;
        this.connectorType = connectorType;
        this.port = port;
        this.username = username;
        this.password = Scrambler.scramble(password);
        this.clientKeyFile = clientKeyFile;
        this.clientTrustFile = clientTrustFile;
        this.artifacts = artifacts;
        this.node = node;
        this.cell = cell;
        this.server = server;
        this.autoStart = autoStart;
        this.clientKeyPassword = Scrambler.scramble(clientKeyPassword);
        this.clientTrustPassword = Scrambler.scramble(clientTrustPassword);
        this.appName = appName;
        this.precompile = precompile;
        this.reloading = reloading;
    }

    public boolean isPrecompile() {
        return precompile;
    }

    public boolean isReloading() {
        return reloading;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getAppName() {
        return appName;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public String getClientKeyPassword() {
        return Scrambler.descramble(clientKeyPassword);
    }

    public String getClientTrustPassword() {
        return Scrambler.descramble(clientTrustPassword);
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public String getClientTrustFile() {
        return clientTrustFile;
    }

    public String getNode() {
        return node;
    }

    public String getServer() {
        return server;
    }

    public String getCell() {
        return cell;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

        if(build.getResult().equals(Result.SUCCESS)) {
            try {
                if(!WebSphere.getInstance().isConnected()) {
                    listener.getLogger().println("Connecting to IBM WebSphere Application Server...");
                    getDescriptor().connectToWebSphere(getConnectorType(),getIpAddress(),getPort(),getUsername(),getPassword(),getClientKeyFile(),getClientKeyPassword(),getClientTrustFile(),getClientTrustPassword());
                    listener.getLogger().println("Connected!");
                } else {
                    listener.getLogger().println("Connected to IBM WebSphere Application Server!");
                }
                J2EEApplication application = WebSphere.getInstance().getApplication(getAppName());
                if(application != null) {
                    listener.getLogger().println("'"+getAppName()+"' Is Already Deployed To WebSphere");
                    if(application.isStarted()) {
                        listener.getLogger().println("Stopping Application '"+getAppName()+"'...");
                        WebSphere.getInstance().stopApplication(getAppName());
                        listener.getLogger().println("Application '"+getAppName()+"' Stopped.");
                    }
                    listener.getLogger().println("Uninstalling Application '"+getAppName()+"'...");
                    WebSphere.getInstance().uninstallApplication(getAppName());
                    listener.getLogger().println("Application '"+getAppName()+"' Uninstalled.");
                }
                FilePath[] paths = build.getWorkspace().sibling("lastSuccessful/").list(getArtifacts());
                if(paths.length == 0) {
                    listener.getLogger().println("No deployable artifacts found in path: "+build.getWorkspace().sibling("lastSuccessful/")+getArtifacts());
                }
                for(FilePath path:paths) {
                    listener.getLogger().println("Deploying '"+getAppName()+"' to IBM WebSphere Application Server");
                    Deployable deployable = new Deployable();
                    deployable.setEarPath(path.getRemote());
                    deployable.setTargetServer(getServer());
                    deployable.setTargetCell(getCell());
                    deployable.setTargetNode(getNode());
                    deployable.setServletReloadingEnabled(isReloading());
                    deployable.setPrecompileJSPs(isPrecompile());
                    WebSphere.getInstance().installApplication(deployable);
                    listener.getLogger().println("Application Successfully Deployed.");
                    if(isAutoStart()) {
                        listener.getLogger().println("Starting Application '"+getAppName()+"'...");
                        WebSphere.getInstance().startApplication("MMA");
                        listener.getLogger().println("Application Started.");
                    }
                }
            } catch (Exception e) {
                listener.getLogger().println("Error deploying to IBM WebSphere Application Server: "+e.getMessage());
                build.setResult(Result.FAILURE);
            }
        }
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String adminClientPath;
        private String orbClientPath;

        public DescriptorImpl() {
            load();
        }

        public FormValidation doTestConnection(@QueryParameter("ipAddress")String ipAddress,
                                               @QueryParameter("connectorType")String connectorType,
                                               @QueryParameter("port")String port,
                                               @QueryParameter("username")String username,
                                               @QueryParameter("password")String password,
                                               @QueryParameter("clientKeyFile")String clientKeyFile,
                                               @QueryParameter("clientTrustFile")String clientTrustFile,
                                               @QueryParameter("clientKeyPassword")String clientKeyPassword,
                                               @QueryParameter("clientTrustPassword")String clientTrustPassword) throws IOException, ServletException {
            try {

                if(!isWebSphereClassloaderAvailable()) {
                    return FormValidation.warning("Cannot Find WebSphere Jar Libraries, Please Copy WebSphere Jar Libraries To Application Server's Classpath To Configure Jenkins. Note: Copying Jars To Jenkins WEB-INF/lib Will Not Work.");
                }
                if(!WebSphere.getInstance().isConnected()) {
                    connectToWebSphere(connectorType,ipAddress,port,username,password,clientKeyFile,clientKeyPassword,clientTrustFile,clientTrustPassword);
                    return FormValidation.ok("Connection Successful!");
                } else {
                    return FormValidation.ok("Already connected!");
                }
            } catch (Exception e) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream p = new PrintStream(out);
                e.printStackTrace(p);
                return FormValidation.error("Connection failed: " + new String(out.toByteArray()));
            }
        }

        public void connectToWebSphere(String connectorType,String ipAddress,String port,String username,String password,String clientKeyFile,String clientKeyPassword,String clientTrustFile,String clientTrustPassword) throws Exception {
            Endpoint endpoint = new Endpoint();
            endpoint.setConnectionType(connectorType);
            endpoint.setPort(port);
            endpoint.setUsername(username);
            endpoint.setPassword(password);
            endpoint.setHost(ipAddress);
            if(username != null && !username.trim().equals("") && !new File(clientKeyFile).exists()) {
                throw new Exception("The path to the client keystore file is incorrect, file was not found");
            }
            endpoint.setClientKeyFile(clientKeyFile);
            endpoint.setClientKeyPassword(clientKeyPassword);
            if(username != null && !username.trim().equals("") && !new File(clientTrustFile).exists()) {
                throw new Exception(("The path to the client truststore file is incorrect, file was not found"));
            }
            endpoint.setClientTrustFile(clientTrustFile);
            endpoint.setClientTrustPassword(clientTrustPassword);
            WebSphere.getInstance().connect(endpoint);
        }

        public FormValidation doCheckPort(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Select a port");
            if (value.length() > 5)
                return FormValidation.warning("Cannot be greater than 65535");
            return FormValidation.ok();
        }

        public FormValidation doCheckAdminClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if(!new File(value).exists()) {
                return FormValidation.error("Path '"+value+"' is not found");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckOrbClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if(!new File(value).exists()) {
                return FormValidation.error("Path '"+value+"' is not found");
            }
            return FormValidation.ok();
        }

        public boolean isWebSphereClassloaderAvailable() {
            try {
                WebSphere.getInstance();
                return true;
            } catch(NoClassDefFoundError e) {
                e.printStackTrace();
            }
            return false;
        }


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Deploy To IBM WebSphere Application Server";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            adminClientPath = formData.getString("adminClientPath");
            orbClientPath = formData.getString("orbClientPath");
            save();
            return super.configure(req,formData);
        }

        public String getAdminClientPath() {
            return adminClientPath;
        }

        public String getOrbClientPath() {
            return orbClientPath;
        }
    }
}

