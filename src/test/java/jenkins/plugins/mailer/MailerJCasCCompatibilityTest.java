package jenkins.plugins.mailer;

import hudson.tasks.Mailer;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MailerJCasCCompatibilityTest extends RoundTripAbstractTest {

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        Mailer.DescriptorImpl descriptor = Mailer.descriptor();
        assertTrue("Mailer can not be found", descriptor != null);
        assertEquals(String.format("Wrong authentication. Username expected %s but received %s", "fakeuser", descriptor.getAuthentication().getUsername()), "fakeuser", descriptor.getAuthentication().getUsername());
        assertEquals(String.format("Wrong charset. Expected %s but received %s", "UTF-8", descriptor.getCharset()), "UTF-8", descriptor.getCharset());
        assertEquals(String.format("Wrong default suffix. Expected %s but received %s", "@mydomain.com", descriptor.getDefaultSuffix()), "@mydomain.com", descriptor.getDefaultSuffix());
        assertEquals(String.format("Wrong ReplayTo address. Expected %s but received %s", "noreplay@mydomain.com", descriptor.getReplyToAddress()), "noreplay@mydomain.com", descriptor.getReplyToAddress());
        assertEquals(String.format("Wrong SMTP host. Expected %s but received %s", "smtp.acme.com", descriptor.getSmtpHost()), "smtp.acme.com", descriptor.getSmtpHost());
        assertEquals(String.format("Wrong SMTP port. Expected %s but received %s", "808080", descriptor.getSmtpPort()), "808080", descriptor.getSmtpPort());
        assertTrue("SSL should be used", descriptor.getUseSsl());
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.tasks.SMTPAuthentication.username = fakeuser";
    }
}
