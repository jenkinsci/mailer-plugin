package hudson.tasks;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.security.FIPS140;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Util;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.ObjectStreamException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class SMTPAuthentication extends AbstractDescribableImpl<SMTPAuthentication> {

    private String username;

    private Secret password;

    @DataBoundConstructor
    public SMTPAuthentication(String username, Secret password) {
        this.username = Util.fixEmptyAndTrim(username);
        this.password = password;
        if (FIPS140.useCompliantAlgorithms() && Secret.toString(password).length() < 14) {
            throw new IllegalArgumentException(jenkins.plugins.mailer.tasks.i18n.Messages.Mailer_SmtpPassNotFipsCompliant());
        }
    }

    public String getUsername() {
        return username;
    }

    public Secret getPassword() {
        return password;
    }

    private Object readResolve() throws ObjectStreamException {
        if (FIPS140.useCompliantAlgorithms() && Secret.toString(password).length() < 14) {
            throw new IllegalStateException("Mailer SMTP password: " + jenkins.plugins.mailer.tasks.i18n.Messages.Mailer_SmtpPassNotFipsCompliant());
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SMTPAuthentication> {

        @Override
        public String getDisplayName() {
            return "Use SMTP Authentication";
        }

        @RequirePOST
        public FormValidation doCheckPassword(@QueryParameter Secret password) {
            if (FIPS140.useCompliantAlgorithms() && Secret.toString(password).length() < 14) {
                return FormValidation.error(jenkins.plugins.mailer.tasks.i18n.Messages.Mailer_SmtpPassNotFipsCompliant());
            }
            return FormValidation.ok();
        }
    }
}
