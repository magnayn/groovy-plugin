package hudson.plugins.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.security.ACL;
import hudson.tasks.Builder;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *  A Builder which executes system Groovy script in Hudson JVM (similar to HUDSON_URL/script).
 * 
 * @author dvrzalik
 */
public class SystemGroovy extends AbstractGroovy {

    //initial variable bindings
    String bindings;
    String classpath;
    Object output;

    @DataBoundConstructor
    public SystemGroovy(ScriptSource scriptSource, String bindings,String classpath) {
        super(scriptSource);
        this.bindings = bindings;
        this.classpath = classpath;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //Hudson.getInstance().checkPermission(Hudson.ADMINISTER); // WTF - always pass, executed as SYSTEM
      
        CompilerConfiguration compilerConfig = new CompilerConfiguration();
        if(classpath != null) {
            compilerConfig.setClasspath(classpath);
        }
        //see RemotingDiagnostics.Script
        ClassLoader cl = Hudson.getInstance().getPluginManager().uberClassLoader;
        if (cl==null)       cl = Thread.currentThread().getContextClassLoader();
        GroovyShell shell = new GroovyShell(cl,new Binding(parseProperties(bindings)),compilerConfig);

        shell.setVariable("out", listener.getLogger());
        output = shell.evaluate(getScriptSource().getScriptStream(build.getWorkspace(),build,listener));
        if (output instanceof Boolean) {
            return (Boolean) output;
        } else {
            if (output != null) {
                listener.getLogger().println("Script returned: " + output);
            }
            
            if (output instanceof Number) {
                return ((Number) output).intValue() == 0;
            }
        }
        //No output. Suppose success.
        return true;
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractGroovyDescriptor {

        DescriptorImpl() {
            super(SystemGroovy.class);
            load();
        }
        
        @Override
        public String getDisplayName() {
            return "Execute system Groovy script";
        }
        
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType){
        	Authentication a = Hudson.getAuthentication();
            if(Hudson.getInstance().getACL().hasPermission(a,Hudson.ADMINISTER)){
            	return true;
            }
        	return false;
        }
        
         @Override
        public Builder newInstance(StaplerRequest req, JSONObject data) throws FormException {
        	//don't allow unauthorized users to modify scripts
        	Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            ScriptSource source = getScriptSource(req, data);
            String binds = data.getString("bindings");
            String classp = data.getString("classpath");
            return new SystemGroovy(source, binds, classp);
         }

        @Override
        public String getHelpFile() {
            return "/plugin/groovy/systemscript-projectconfig.html";
        }
    }

    //---- Backward compatibility -------- //
    
    public enum BuilderType { COMMAND,FILE }
    
    private String command;
    
    private Object readResolve() {
        if(command != null) {
            scriptSource = new StringScriptSource(command);
            command = null;
        }

        return this;
    }
    
    public String getCommand() {
        return command;
    }

    public String getBindings() {
        return bindings;
    }

    public String getClasspath() {
        return classpath;
    }

    public Object getOutput() {
        return output;
    }
}
