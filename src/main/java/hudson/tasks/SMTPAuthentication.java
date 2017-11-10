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

    private String smtpAuthUsername;

    private Secret smtpAuthPassword;

    @DataBoundConstructor
    public SMTPAuthentication(String smtpAuthUsername, Secret smtpAuthPassword) {
        this.smtpAuthUsername = smtpAuthUsername;
        this.smtpAuthPassword = smtpAuthPassword;
    }

    public String getSmtpAuthUsername() {
        return smtpAuthUsername;
    }

    public Secret getSmtpAuthPassword() {
        return smtpAuthPassword;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SMTPAuthentication> {

        @Override
        public String getDisplayName() {
            return "Use SMTP Authentication";
        }
    }
}
