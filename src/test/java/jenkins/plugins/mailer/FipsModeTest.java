package jenkins.plugins.mailer;

import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.tasks.Mailer;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.Serializable;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

class FipsModeTest {

    private static final String SHORT_PWD_ERROR_MESSAGE = "When running in FIPS compliance mode, the password must be at least 14 characters long";
    @RegisterExtension
    private final RealJenkinsExtension r = new RealJenkinsExtension().javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true").withDebugPort(5008);

    @Test
    @LocalData
    void testBlowsUpOnStart() throws Throwable {
        r.then(FipsModeTest::verifyOldData);
    }

    static void verifyOldData(JenkinsRule j) {
        OldDataMonitor monitor = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Mailer.DescriptorImpl descriptor = Mailer.descriptor();
        assertNull(descriptor.getAuthentication());
        OldDataMonitor.VersionRange versionRange = monitor.getData().get(descriptor);
        assertNotNull(versionRange);
        assertThat(versionRange.extra, containsString("Mailer SMTP password: " + SHORT_PWD_ERROR_MESSAGE));
    }

    @Test
    @LocalData
    void casc() throws Throwable {
        URL url = getClass().getResource("bad_fips_casc.yaml");
        r.then(new _casc(url.toString()));
    }

    public static class _casc implements RealJenkinsExtension.Step2<Serializable> {
        String resUrl;

        public _casc(String resUrl) {
            this.resUrl = resUrl;
        }

        @Override
        public Serializable run(JenkinsRule r) {
            ConfiguratorException e = assertThrows(ConfiguratorException.class, () -> ConfigurationAsCode.get().configure(resUrl));
            Throwable cause = e.getCause();
            assertNotNull(cause);
            cause = cause.getCause();
            assertThat(cause, instanceOf(IllegalArgumentException.class));
            assertThat(cause.getMessage(), containsString(SHORT_PWD_ERROR_MESSAGE));
            return null;
        }
    }
}
