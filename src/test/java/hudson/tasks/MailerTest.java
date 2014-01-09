/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks;

import hudson.model.FreeStyleProject;
import hudson.tasks.Mailer.DescriptorImpl;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Email;
import org.jvnet.mock_javamail.Mailbox;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import jenkins.model.JenkinsLocationConfiguration;


/**
 * @author Kohsuke Kawaguchi
 */
public class MailerTest extends HudsonTestCase {
    @Bug(1566)
    public void testSenderAddress() throws Exception {
        // intentionally give the whole thin in a double quote
        JenkinsLocationConfiguration.get().setAdminAddress("\"me <me@sun.com>\"");

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        // create a project to simulate a build failure
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new FailureBuilder());
        Mailer m = new Mailer(recipient, false, false, false);
        project.getPublishersList().add(m);

        project.scheduleBuild2(0).get();

        assertEquals(1,yourInbox.size());
        Address[] senders = yourInbox.get(0).getFrom();
        assertEquals(1,senders.length);
        assertEquals("me <me@sun.com>",senders[0].toString());
    }

    @Email("http://www.nabble.com/email-recipients-disappear-from-freestyle-job-config-on-save-to25479293.html")
    public void testConfigRoundtrip() throws Exception {
        Mailer m = new Mailer("kk@kohsuke.org", false, true, false);
        verifyRoundtrip(m);

        m = new Mailer("", true, false, false);
        verifyRoundtrip(m);
    }

    private void verifyRoundtrip(Mailer before) throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(before);
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
        Mailer after = p.getPublishersList().get(Mailer.class);
        assertNotSame(before,after);
        assertEqualBeans(before,after,"recipients,dontNotifyEveryUnstableBuild,sendToIndividuals");
    }

    public void testGlobalConfigRoundtrip() throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        JenkinsLocationConfiguration.get().setAdminAddress("admin@me");
        d.setDefaultSuffix("default-suffix");
        d.setHudsonUrl("http://nowhere/");
        d.setSmtpHost("smtp.host");
        d.setSmtpPort("1025");
        d.setUseSsl(true);
        d.setSmtpAuth("user","pass");

        submit(new WebClient().goTo("configure").getFormByName("config"));

        assertEquals("admin@me", JenkinsLocationConfiguration.get().getAdminAddress());
        assertEquals("default-suffix",d.getDefaultSuffix());
        assertEquals("http://nowhere/",d.getUrl());
        assertEquals("smtp.host",d.getSmtpServer());
        assertEquals("1025",d.getSmtpPort());
        assertEquals(true,d.getUseSsl());
        assertEquals("user",d.getSmtpAuthUserName());
        assertEquals("pass",d.getSmtpAuthPassword());

        d.setUseSsl(false);
        d.setSmtpAuth(null,null);
        submit(new WebClient().goTo("configure").getFormByName("config"));
        assertEquals(false,d.getUseSsl());
        assertNull("expected null, got: " + d.getSmtpAuthUserName(), d.getSmtpAuthUserName());
        assertNull("expected null, got: " + d.getSmtpAuthPassword(), d.getSmtpAuthPassword());
    }
    
    /**
     * Simulates {@link JenkinsLocationConfiguration} is not configured.
     */
    private static class CleanJenkinsLocationConfiguration extends JenkinsLocationConfiguration {
        @Override
        public synchronized void load() {
            getConfigFile().delete();
            super.load();
        }
    };
    
    /**
     * Test {@link JenkinsLocationConfiguration} can load hudsonUrl.
     */
    public void testHudsonUrlCompatibility() throws Exception {
        // not configured.
        assertNull(new CleanJenkinsLocationConfiguration().getUrl());
        
        Mailer m = new Mailer("", true, false, false);
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(m);
        WebClient wc = new WebClient();
        submit(wc.getPage(p,"configure").getFormByName("config"));
        
        // configured via the marshaled XML file of Mailer
        assertEquals(wc.getContextPath(), new CleanJenkinsLocationConfiguration().getUrl());
    }
}
