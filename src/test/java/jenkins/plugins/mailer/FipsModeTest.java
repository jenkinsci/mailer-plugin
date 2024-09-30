package jenkins.plugins.mailer;

import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.tasks.Mailer;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class FipsModeTest {
    public static final String SHORT_PWD_ERROR_MESSAGE = "When running in FIPS compliance mode, the password must be at least 14 characters long";
    @Rule
    public RealJenkinsRule r = new RealJenkinsRule().javaOptions("-Djenkins.security.FIPS140.COMPLIANCE=true").withDebugPort(5008);

    @Test @LocalData
    public void testBlowsUpOnStart() throws Throwable {
        r.then(FipsModeTest::verifyOldData);
    }

    static void verifyOldData(JenkinsRule j) throws Throwable {
        OldDataMonitor monitor = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Mailer.DescriptorImpl descriptor = Mailer.descriptor();
        assertNull(descriptor.getAuthentication());
        OldDataMonitor.VersionRange versionRange = monitor.getData().get(descriptor);
        assertNotNull(versionRange);
        assertThat(versionRange.extra, containsString("Mailer SMTP password: " + SHORT_PWD_ERROR_MESSAGE));
    }

    @Test
    public void testConfig() throws Throwable {
        r.then(FipsModeTest::_testConfig);
    }

    public static void _testConfig(JenkinsRule j) throws Exception {
        Assume.assumeThat("TODO the form elements for email-ext have the same names", j.getPluginManager().getPlugin("email-ext"), is(nullValue()));
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage cp = wc.goTo("configure");
            wc.setThrowExceptionOnFailingStatusCode(false);
            HtmlForm form = cp.getFormByName("config");

            form.getInputByName("_.smtpHost").setValue("acme.com");
            form.getInputByName("_.defaultSuffix").setValue("@acme.com");
            form.getInputByName("_.authentication").setChecked(true);
            form.getInputByName("_.username").setValue("user");
            form.getInputByName("_.password").setValue("pass");
            wc.waitForBackgroundJavaScript(1000);
            assertThat(form.getTextContent(), containsString(SHORT_PWD_ERROR_MESSAGE));
            HtmlPage page = j.submit(form);
            WebResponse webResponse = page.getWebResponse();
            assertNotEquals(200, webResponse.getStatusCode());
            assertThat(webResponse.getContentAsString(), containsString(SHORT_PWD_ERROR_MESSAGE));
        }

    }

    @Test @LocalData
    public void casc() throws Throwable {
        URL url = getClass().getResource("bad_fips_casc.yaml");
        r.then(new _casc(url.toString()));
    }

    public static class _casc implements RealJenkinsRule.Step2<Serializable> {
        String resUrl;

        public _casc(String resUrl) {
            this.resUrl = resUrl;
        }

        @Override
        public Serializable run(JenkinsRule r) throws Throwable {
            try {
                ConfigurationAsCode.get().configure(resUrl);
                fail("The configuration should fail.");
            } catch (ConfiguratorException e) {
                Throwable cause = e.getCause();
                assertNotNull(cause);
                cause = cause.getCause();
                assertThat(cause, instanceOf(IllegalArgumentException.class));
                assertThat(cause.getMessage(), containsString(SHORT_PWD_ERROR_MESSAGE));
            }
            return null;
        }
    }

}
