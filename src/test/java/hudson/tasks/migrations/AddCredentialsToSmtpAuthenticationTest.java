package hudson.tasks.migrations;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.tasks.Mailer;
import hudson.tasks.SMTPAuthentication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AddCredentialsToSmtpAuthenticationTest extends MigrationTest {
    @Override
    protected void change(Mailer.DescriptorImpl descriptor) {
        final SMTPAuthentication authentication = descriptor.getAuthentication();

        assertNotNull(authentication);

        final String credentialsId = authentication.getCredentialsId();

        assertNotNull(credentialsId);

        final StandardUsernamePasswordCredentials migratedCredential = Util.lookupCredential(credentialsId)
                .orElseThrow(() -> new RuntimeException("Can't find the migrated test credential"));

        assertEquals("olduser", migratedCredential.getUsername());
        assertEquals("{AQAAABAAAAAQ1UuHpGkqtUa56seSp+wJjfuiggZPi/D+t38985a5tXU=}", migratedCredential.getPassword().getPlainText());
    }
}
