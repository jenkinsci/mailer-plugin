package hudson.tasks;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import hudson.Util;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class SMTPAuthentication extends AbstractDescribableImpl<SMTPAuthentication> {

    private static final Logger LOGGER = Logger.getLogger(SMTPAuthentication.class.getName());

    /** use StandardUsernamePasswordCredentials instead */
    @Deprecated
    private transient String username;

    /** use StandardUsernamePasswordCredentials instead */
    @Deprecated
    private transient Secret password;

    /** The ID of the Jenkins Username/Password credential to use. */
    private String credentialsId;

    @DataBoundConstructor
    public SMTPAuthentication(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    SMTPAuthentication(String username, Secret password) {
        this.username = Util.fixEmptyAndTrim(username);
        this.password = password;
        readResolve();
    }

    @Deprecated
    public String getUsername() {
        return username;
    }

    @Deprecated
    public Secret getPassword() {
        return password;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    private Object readResolve() {
        if (StringUtils.isBlank(credentialsId) && (username != null || password != null)) {
            LOGGER.log(Level.CONFIG, "Migrating the Mailer SMTP authentication details to credential...");

            final StandardUsernamePasswordCredentials migratedCredential = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL,
                    null,
                    "Mailer SMTP authentication credentials (migrated)",
                    username,
                    password.getPlainText());

            SystemCredentialsProvider.getInstance().getCredentials().add(migratedCredential);

            credentialsId = migratedCredential.getId();
        }

        username = null;
        password = null;

        return this;
    }



    @Extension
    public static class DescriptorImpl extends Descriptor<SMTPAuthentication> {

        @Override
        public String getDisplayName() {
            return "Use SMTP Authentication";
        }

        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result
                    .includeMatchingAs(ACL.SYSTEM, (Item) null, StandardUsernamePasswordCredentials.class, Collections.emptyList(), CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }
    }
}
