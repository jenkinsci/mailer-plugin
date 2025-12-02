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

import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.DumbSlave;
import hudson.tasks.Mailer.DescriptorImpl;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.mock_javamail.Mailbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MailerTest {
    /** Default recipient used in tests. */
    private static final String RECIPIENT = "you <you@sun.com>";
    /** Fixed FAILURE builder. */
    private static final Builder FAILURE = new FailureBuilder();
    /** Fixed UNSTABLE builder. */
    private static final Builder UNSTABLE = new UnstableBuilder();
    /** Commit author 1. */
    private static final String AUTHOR1 = "author1@example.com";
    /** Commit author 2. */
    private static final String AUTHOR2 = "author2@example.com";
    /** Change counter. */
    private static final AtomicLong COUNTER = new AtomicLong(0L);

    private static Mailbox getMailbox(String recipient) throws Exception {
        return Mailbox.get(new InternetAddress(recipient));
    }

    @BeforeAll
    static void enableManagePermission() {
        // TODO remove when baseline contains https://github.com/jenkinsci/jenkins/pull/23873
        Jenkins.MANAGE.setEnabled(true);
    }

    private static Mailbox getEmptyMailbox(String recipient) throws Exception {
        Mailbox inbox = getMailbox(recipient);
        inbox.clear();
        return inbox;
    }

    private TestProject create(JenkinsRule rule, boolean notifyEveryUnstableBuild, boolean sendToIndividuals) throws Exception {
        Mailer m = new Mailer(RECIPIENT, notifyEveryUnstableBuild, sendToIndividuals);
        return new TestProject(rule, m);
    }

    @Test
    void testFixEmptyAndTrimOne(JenkinsRule rule) throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        d.setSmtpHost("smtp.host");
        d.setAuthentication(new SMTPAuthentication("user", Secret.fromString("pass")));
        d.setSmtpPort("1025");
        d.setReplyToAddress("foo@bar.com");
        d.setCharset("UTF-8");

        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));

        assertEquals("smtp.host", d.getSmtpHost());
        SMTPAuthentication authentication = d.getAuthentication();
        assertEquals("user", authentication.getUsername());
        assertEquals("pass", authentication.getPassword().getPlainText());
        assertEquals("1025", d.getSmtpPort());
        assertEquals("foo@bar.com", d.getReplyToAddress());
        assertEquals("UTF-8", d.getCharset());
    }

    @Test
    void testFixEmptyAndTrimTwo(JenkinsRule rule) throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        d.setSmtpHost("   smtp.host   ");
        d.setAuthentication(new SMTPAuthentication("   user   ", Secret.fromString("pass")));
        d.setSmtpPort("   1025   ");
        d.setReplyToAddress("   foo@bar.com   ");
        d.setCharset("   UTF-8   ");

        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));

        assertEquals("smtp.host", d.getSmtpHost());
        SMTPAuthentication authentication = d.getAuthentication();
        assertEquals("user", authentication.getUsername());
        assertEquals("pass", authentication.getPassword().getPlainText());
        assertEquals("1025", d.getSmtpPort());
        assertEquals("foo@bar.com", d.getReplyToAddress());
        assertEquals("UTF-8", d.getCharset());
    }

    @Test
    void testFixEmptyAndTrimThree(JenkinsRule rule) throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        d.setSmtpHost("");
        d.setAuthentication(new SMTPAuthentication("", Secret.fromString("pass")));
        d.setSmtpPort("");
        d.setReplyToAddress("");
        d.setCharset("");

        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));

        assertNull(d.getSmtpHost());
        SMTPAuthentication authentication = d.getAuthentication();
        assertNull(authentication.getUsername());
        assertEquals("pass", authentication.getPassword().getPlainText());
        assertNull(d.getSmtpPort());
        assertNull(d.getReplyToAddress());
        assertEquals("UTF-8", d.getCharset());
    }

    @Test
    void testFixEmptyAndTrimFour(JenkinsRule rule) throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        d.setSmtpHost(null);
        d.setAuthentication(new SMTPAuthentication(null, Secret.fromString("pass")));
        d.setSmtpPort(null);
        d.setReplyToAddress(null);
        d.setCharset(null);

        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));

        assertNull(d.getSmtpHost());
        SMTPAuthentication authentication = d.getAuthentication();
        assertNull(authentication.getUsername());
        assertEquals("pass", authentication.getPassword().getPlainText());
        assertNull(d.getSmtpPort());
        assertNull(d.getReplyToAddress());
        assertEquals("UTF-8", d.getCharset());
    }

    @Issue("JENKINS-1566")
    @Test
    void testSenderAddress(JenkinsRule rule) throws Exception {
        // intentionally give the whole thin in a double quote
        JenkinsLocationConfiguration.get().setAdminAddress("\"me <me@sun.com>\"");

        // create a project to simulate a build failure
        TestProject project = create(rule, false, false).failure().buildAndCheck(1);

        // build it and check sender address
        Mailbox yourInbox = getMailbox(RECIPIENT);
        Address[] senders = yourInbox.get(0).getFrom();
        assertEquals(1, senders.length);
        assertEquals("me <me@sun.com>", senders[0].toString());
    }

    @Email("http://www.nabble.com/email-recipients-disappear-from-freestyle-job-config-on-save-to25479293.html")
    @Test
    void testConfigRoundtrip(JenkinsRule rule) throws Exception {
        Mailer m = new Mailer("kk@kohsuke.org", false, true);
        verifyRoundtrip(rule, m);

        m = new Mailer("", true, false);
        verifyRoundtrip(rule, m);
    }

    private void verifyRoundtrip(JenkinsRule rule, Mailer before) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getPublishersList().add(before);
        rule.submit(rule.createWebClient().getPage(p, "configure").getFormByName("config"));
        Mailer after = p.getPublishersList().get(Mailer.class);
        assertNotSame(before, after);
        rule.assertEqualBeans(before, after, "recipients,dontNotifyEveryUnstableBuild,sendToIndividuals");
    }

    @Test
    void testGlobalConfigRoundtrip(JenkinsRule rule) throws Exception {
        DescriptorImpl d = Mailer.descriptor();
        JenkinsLocationConfiguration.get().setAdminAddress("admin@me");
        d.setDefaultSuffix("default-suffix");
        d.setSmtpHost("smtp.host");
        d.setSmtpPort("1025");
        d.setUseSsl(true);
        d.setAuthentication(new SMTPAuthentication("user", Secret.fromString("pass")));

        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));

        assertEquals("admin@me", JenkinsLocationConfiguration.get().getAdminAddress());
        assertEquals("default-suffix", d.getDefaultSuffix());
        assertEquals("smtp.host", d.getSmtpHost());
        assertEquals("1025", d.getSmtpPort());
        assertTrue(d.getUseSsl());
        SMTPAuthentication authentication = d.getAuthentication();
        assertEquals("user", authentication.getUsername());
        assertEquals("pass", authentication.getPassword().getPlainText());

        d.setUseSsl(false);
        d.setAuthentication(null);
        rule.submit(rule.createWebClient().goTo("configure").getFormByName("config"));
        assertFalse(d.getUseSsl());
        assertNull(d.getAuthentication());
    }

    @Test
    void globalConfig(JenkinsRule rule) throws Exception {
        assumeTrue(rule.getPluginManager().getPlugin("email-ext") == null, "TODO the form elements for email-ext have the same names");

        WebClient webClient = rule.createWebClient();
        HtmlPage cp = webClient.goTo("configure");
        HtmlForm form = cp.getFormByName("config");

        form.getInputByName("_.smtpHost").setValue("acme.com");
        form.getInputByName("_.defaultSuffix").setValue("@acme.com");
        form.getInputByName("_.authentication").setChecked(true);
        form.getInputByName("_.username").setValue("user");
        form.getInputByName("_.password").setValue("pass");

        rule.submit(form);

        DescriptorImpl d = Mailer.descriptor();
        assertEquals("acme.com", d.getSmtpHost());
        assertEquals("@acme.com", d.getDefaultSuffix());
        SMTPAuthentication auth = d.getAuthentication();
        assertNotNull(auth);
        assertEquals("user", auth.getUsername());
        assertEquals("pass", auth.getPassword().getPlainText());

        cp = webClient.goTo("configure");
        form = cp.getFormByName("config");
        form.getInputByName("_.authentication").setChecked(false);
        rule.submit(form);

        assertNull(d.getAuthentication());
    }

    @Test
    void authenticationFormValidation(JenkinsRule rule) {
        DescriptorImpl d = Mailer.descriptor();

        // no authentication without TLS/SSL
        assertThat(d.doCheckAuthentication(false, false, false), is(FormValidation.ok()));

        // no authentication with TLS / SSL combos
        assertThat(d.doCheckAuthentication(false, true, false), is(FormValidation.ok()));
        assertThat(d.doCheckAuthentication(false, false, true), is(FormValidation.ok()));
        assertThat(d.doCheckAuthentication(false, true, true), is(FormValidation.ok()));

        // authentication without TLS/SSL
        assertThat(d.doCheckAuthentication(true, false, false).kind, is(FormValidation.Kind.WARNING));

        // authentication with TSL / SSL combos
        assertThat(d.doCheckAuthentication(true, true, false), is(FormValidation.ok()));
        assertThat(d.doCheckAuthentication(true, false, true), is(FormValidation.ok()));
        assertThat(d.doCheckAuthentication(true, true, true), is(FormValidation.ok()));
    }

    /**
     * Simulates {@link JenkinsLocationConfiguration} is not configured.
     */
    private static class CleanJenkinsLocationConfiguration extends JenkinsLocationConfiguration {
        public CleanJenkinsLocationConfiguration() {
            super();
            load();
        }

        @Override
        public synchronized void load() {
            try {
                getConfigFile().delete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            super.load();
        }
    }

    /**
     * Test {@link JenkinsLocationConfiguration} can load hudsonUrl.
     */
    @Test
    void testHudsonUrlCompatibility(JenkinsRule rule) throws Exception {
        // not configured.
        assertNull(new CleanJenkinsLocationConfiguration().getUrl());

        Mailer m = new Mailer("", true, false);
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getPublishersList().add(m);
        WebClient wc = rule.createWebClient();
        rule.submit(wc.getPage(p, "configure").getFormByName("config"));

        // configured via the marshaled XML file of Mailer
        assertEquals(wc.getContextPath(), new CleanJenkinsLocationConfiguration().getUrl());
    }

    @Test
    void testNotifyEveryUnstableBuild(JenkinsRule rule) throws Exception {
        create(rule, true, false).failure()
            .buildAndCheck(1)
            .buildAndCheck(1)
            .unstable()
            .buildAndCheck(1)
            .buildAndCheck(1)
            .success()
            .buildAndCheck(1) /* back to normal mail. */
            .buildAndCheck(0)
            .unstable()
            .buildAndCheck(1)
            .failure()
            .buildAndCheck(1);
    }

    @Test
    void testNotNotifyEveryUnstableBuild(JenkinsRule rule) throws Exception {
        create(rule, false, false)
            .buildAndCheck(0)
            .buildAndCheck(0)
            .unstable()
            .buildAndCheck(1)
            .buildAndCheck(0)
            .success()
            .buildAndCheck(1) /* back to normal mail. */
            .buildAndCheck(0)
            .unstable()
            .buildAndCheck(1)
            .buildAndCheck(0)
            .failure()
            .buildAndCheck(1)
            .buildAndCheck(1);
    }

    @SuppressWarnings("deprecation")
    @Test
    void testNotifyCulprits(JenkinsRule rule) throws Exception {
        MailSender.debug = true;
        try {
            rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
            User.get("author1").addProperty(new Mailer.UserProperty(AUTHOR1));
            User.get("author2").addProperty(new Mailer.UserProperty(AUTHOR2));
            TestProject project = create(rule, true, true).buildAndCheck(0);
            // Changes with no problems
            project.commit("author1").clearBuild().check(0, 0, 0);
            // Commit causes build to be unstable
            project.commit("author1").unstable().clearBuild().check(1, 1, 0);
            // Another one
            project.commit("author2").clearBuild().check(1, 1, 1);
            // Back to normal
            project.commit("author1").success().clearBuild().check(1, 1, 1);
            // Now a failure
            project.commit("author2").failure().clearBuild().check(1, 0, 1);
        } finally {
            MailSender.debug = false;
        }
    }

    @Issue("JENKINS-40224")
    @Test
    void testMessageText(JenkinsRule rule) throws Exception {
        create(rule, true, false)
            .failure()
            .buildAndCheckContent()
            .unstable()
            .buildAndCheckContent()
            .success()
            .buildAndCheckContent();
    }

    @Issue("JENKINS-37812")
    @Test
    void testFailureSendMessage(JenkinsRule rule) throws Exception {
        DumbSlave node = rule.createSlave();
        Mailer m = new MailerDisconnecting(rule, node, RECIPIENT, true, false);

        new TestProject(rule, m)
                .withNode(node)
                .failure()
                .buildAndCheckSending();
    }

    @Issue("JENKINS-37812")
    @Test
    void testPipelineCompatibility(JenkinsRule rule) throws Exception {
        WorkflowJob p = rule.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "    catchError {error 'oops'}\n"
                        + "    step([$class: 'Mailer', recipients: '" + RECIPIENT + "'])\n"
                        + "}", true));

        Mailbox inbox = getMailbox(RECIPIENT);
        inbox.clear();
        WorkflowRun b = rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertThat("One email should have been sent", inbox, hasSize(1));
        assertEquals("Build failed in Jenkins: " + b.getFullDisplayName(), inbox.get(0).getSubject());
        assertThat(rule.getLog(b), containsString("Sending e-mails to"));
    }

    @Issue("JENKINS-55109")
    @Test
    @LocalData
    void testMigrateOldData(JenkinsRule rule) {
        Mailer.DescriptorImpl descriptor = Mailer.descriptor();
        assertNotNull(descriptor, "Mailer can not be found");
        assertEquals("olduser", descriptor.getAuthentication().getUsername(), String.format("Authentication did not migrate properly. Username expected %s but received %s", "olduser", descriptor.getAuthentication().getUsername()));
        assertEquals("UTF-8", descriptor.getCharset(), String.format("Charset did not migrate properly. Expected %s but received %s", "UTF-8", descriptor.getCharset()));
        assertEquals("@mydomain.com", descriptor.getDefaultSuffix(), String.format("Default suffix did not migrate properly. Expected %s but received %s", "@mydomain.com", descriptor.getDefaultSuffix()));
        assertEquals("noreplay@mydomain.com", descriptor.getReplyToAddress(), String.format("ReplayTo address did not migrate properly. Expected %s but received %s", "noreplay@mydomain.com", descriptor.getReplyToAddress()));
        assertEquals("old.data.smtp.host", descriptor.getSmtpHost(), String.format("SMTP host did not migrate properly. Expected %s but received %s", "old.data.smtp.host", descriptor.getSmtpHost()));
        assertEquals("808080", descriptor.getSmtpPort(), String.format("SMTP port did not migrate properly. Expected %s but received %s", "808080", descriptor.getSmtpPort()));
        assertTrue(descriptor.getUseSsl(), "SSL should be used");
    }

    @Test
    void managePermissionShouldAccessGlobalConfig(JenkinsRule rule) {
        final String USER = "user";
        final String MANAGER = "manager";
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        rule.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   // Read access
                                                   .grant(Jenkins.READ).everywhere().to(USER)

                                                   // Read and Manage
                                                   .grant(Jenkins.READ).everywhere().to(MANAGER)
                                                   .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
        );

        try (ACLContext c = ACL.as(User.getById(USER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            assertThat("Global configuration should not be accessible to READ users", descriptors, is(empty()));
        }

        try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            Optional<Descriptor> found =
                    descriptors.stream().filter(descriptor -> descriptor instanceof Mailer.DescriptorImpl).findFirst();
            assertTrue(found.isPresent(), "Global configuration should be accessible to MANAGE users");
        }
    }

    @Test
    @Issue("SECURITY-2163")
    void doCheckSmtpServerShouldThrowExceptionForUserWithoutManagePermissions(JenkinsRule rule) {
        final String USER = "user";
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        rule.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(USER)
        );
        final String expectedErrorMessage = "user is missing the Overall/Manage permission";

        try (ACLContext ignored = ACL.as(User.getById(USER, true))) {
            RuntimeException runtimeException = assertThrows(RuntimeException.class,
                    () -> Mailer.descriptor().doCheckSmtpHost("domain.com"),
                    expectedErrorMessage);
            assertThat(runtimeException.getMessage(), containsString(expectedErrorMessage));
        }
    }

    @Test
    @Issue("SECURITY-2163")
    void doCheckSmtpServerShouldNotThrowForUserWithManagePermissions(JenkinsRule rule) {
        final String MANAGER = "manage";
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        rule.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
        );

        try (ACLContext ignored = ACL.as(User.getById(MANAGER, true))) {
            Mailer.descriptor().doCheckSmtpHost("domain.com");
        }
    }

    private static final class TestProject {
        private final JenkinsRule rule;
        private final FakeChangeLogSCM scm = new FakeChangeLogSCM();
        private final FreeStyleProject project;

        TestProject(JenkinsRule rule, Mailer m) throws Exception {
            this.rule = rule;
            project = rule.createFreeStyleProject();
            project.setScm(scm);
            project.getPublishersList().add(m);
        }

        TestProject withNode(DumbSlave node) throws Exception {
            project.setAssignedNode(node);
            return this;
        }

        TestProject changeStatus(Builder status) {
            project.getBuildersList().clear();
            if (status != null) {
                project.getBuildersList().add(status);
            }
            return this;
        }

        TestProject success() {
            return changeStatus(null);
        }

        TestProject failure() {
            return changeStatus(FAILURE);
        }

        TestProject unstable() {
            return changeStatus(UNSTABLE);
        }

        TestProject commit(String author) {
            scm.addChange().withAuthor(author).withMsg("commit #" + COUNTER.incrementAndGet());
            return this;
        }

        TestBuild build(String... mailboxesToClear) throws Exception {
            for (String recipient : mailboxesToClear) {
                getEmptyMailbox(recipient);
            }
            return new TestBuild(rule, project.scheduleBuild2(0).get());
        }

        TestBuild clearBuild() throws Exception {
            return build(RECIPIENT, AUTHOR1, AUTHOR2);
        }

        TestProject buildAndCheck(int expectedSize, String recipient) throws Exception {
            build(recipient).check(expectedSize, recipient);
            return this;
        }

        TestProject buildAndCheck(int expectedSize) throws Exception {
            return buildAndCheck(expectedSize, RECIPIENT);
        }

        TestProject buildAndCheckContent() throws Exception {
            build(RECIPIENT).checkContent();

            return this;
        }

        TestProject buildAndCheckSending() throws Exception {
            build(RECIPIENT).checkSendingContent();

            return this;
        }
    }

    private static final class TestBuild {
        private final JenkinsRule rule;
        private final FreeStyleBuild build;
        private final String log;

        TestBuild(JenkinsRule rule, FreeStyleBuild build) throws Exception {
            this.rule = rule;
            this.build = build;
            this.log = rule.getLog(build);
        }

        TestBuild check(int expectedSize, String recipient) throws Exception {
            final Mailbox inbox = getMailbox(recipient);
            assertEquals(expectedSize, inbox.size(), log);
            return this;
        }

        TestBuild check(int expectedRecipient, int expectedAuthor1, int expectedAuthor2) throws Exception {
            return check(expectedRecipient, RECIPIENT).check(expectedAuthor1, AUTHOR1).check(expectedAuthor2, AUTHOR2);
        }

        void checkContent() throws Exception {
            String expectedInMessage = String.format("<%sjob/%s/%d/display/redirect>\n\n", rule.getURL(), this.build.getProject().getName(), this.build.getNumber());
            String emailContent = getMailbox(RECIPIENT).get(0).getContent().toString();
            assertThat(emailContent, containsString(expectedInMessage));
        }

        void checkSendingContent() throws Exception {
            Mailbox inbox = getMailbox(RECIPIENT);
            assertThat("One email should have been sent", inbox, hasSize(1));
            assertThat(log, containsString("Sending e-mails to"));
        }
    }

    private static final class MailerDisconnecting extends Mailer {
        private final transient JenkinsRule rule;
        private final transient DumbSlave node;

        private MailerDisconnecting(JenkinsRule rule, DumbSlave node, String recipients, boolean notifyEveryUnstableBuild, boolean sendToIndividuals) {
            super(recipients, notifyEveryUnstableBuild, sendToIndividuals);
            this.rule = rule;
            this.node = node;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            try {
                rule.disconnectSlave(node);
            } catch (Exception e) {
                throw new IOException(String.format("Impossible to disconnect %s", node.getDisplayName()), e);
            }

            return super.perform(build, launcher, listener);
        }

        @TestExtension
        public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

            private SMTPAuthentication authentication;

            public DescriptorImpl() {
                load();
            }

            public String getDisplayName() {
                return "TestDescriptor";
            }

            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            public SMTPAuthentication getAuthentication() {
                return authentication;
            }
        }
    }
}
