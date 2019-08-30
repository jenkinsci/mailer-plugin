package hudson.tasks;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class SMTPAuthentication extends AbstractDescribableImpl<SMTPAuthentication> {

    private String username;

    private Secret password;

    @DataBoundConstructor
    public SMTPAuthentication(String username, Secret password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public Secret getPassword() {
        return password;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SMTPAuthentication> {

        @Override
        public String getDisplayName() {
            return "Use SMTP Authentication";
        }
    }
}
