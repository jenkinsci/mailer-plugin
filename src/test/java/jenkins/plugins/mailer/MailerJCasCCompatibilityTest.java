package jenkins.plugins.mailer;

import hudson.tasks.Mailer;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class MailerJCasCCompatibilityTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule jenkinsRule, String s) {
        Mailer.DescriptorImpl descriptor = Mailer.descriptor();
        assertNotNull(descriptor, "Mailer can not be found");
        assertEquals("fakeuser", descriptor.getAuthentication().getUsername(), String.format("Wrong authentication. Username expected %s but received %s", "fakeuser", descriptor.getAuthentication().getUsername()));
        assertEquals("UTF-8", descriptor.getCharset(), String.format("Wrong charset. Expected %s but received %s", "UTF-8", descriptor.getCharset()));
        assertEquals("@mydomain.com", descriptor.getDefaultSuffix(), String.format("Wrong default suffix. Expected %s but received %s", "@mydomain.com", descriptor.getDefaultSuffix()));
        assertEquals("noreplay@mydomain.com", descriptor.getReplyToAddress(), String.format("Wrong ReplayTo address. Expected %s but received %s", "noreplay@mydomain.com", descriptor.getReplyToAddress()));
        assertEquals("smtp.acme.com", descriptor.getSmtpHost(), String.format("Wrong SMTP host. Expected %s but received %s", "smtp.acme.com", descriptor.getSmtpHost()));
        assertEquals("808080", descriptor.getSmtpPort(), String.format("Wrong SMTP port. Expected %s but received %s", "808080", descriptor.getSmtpPort()));
        assertTrue(descriptor.getUseSsl(), "SSL should be used");
        assertTrue(descriptor.getUseTls(), "TLS should be used");
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.tasks.SMTPAuthentication.username = fakeuser";
    }
}
