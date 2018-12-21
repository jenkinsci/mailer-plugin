package hudson.tasks;

import com.google.common.collect.Sets;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.util.StreamTaskListener;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.plugins.mailer.tasks.i18n.Messages;
import org.acegisecurity.Authentication;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import static org.mockito.Mockito.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Test case for the {@link MailSender}
 * 
 * See also {@link MailerTest} for more tests for the mailer.
 * 
 * @author Christoph Kutzinski
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
@PowerMockIgnore({"javax.security.auth.Subject", "javax.security.*", "javax.xml.*"}) // otherwise as in https://groups.google.com/d/msg/jenkinsci-dev/n5sdCxrccSk/7K4yTTc7XG4J mock(ACL.class) in Java 8 fails with: java.lang.LinkageError: loader constraint violation in interface itable initialization: when resolving method "org.acegisecurity.Authentication$$EnhancerByMockitoWithCGLIB$$31bf4863.implies(Ljavax/security/auth/Subject;)Z" the class loader (instance of org/powermock/core/classloader/MockClassLoader) of the current class, org/acegisecurity/Authentication$$EnhancerByMockitoWithCGLIB$$31bf4863, and the class loader (instance of <bootloader>) for interface java/security/Principal have different Class objects for the type javax/security/auth/Subject used in the signature
@SuppressWarnings("rawtypes")
public class MailSenderTest {
    
    /**
     * Tests that all culprits from the previous builds upstream build (exclusive)
     * until the current builds upstream build (inclusive) are contained in the recipients
     * list.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testIncludeUpstreamCulprits() throws Exception {
        final Jenkins jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(jenkins.isUseSecurity()).thenReturn(false);
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.doReturn(jenkins).when(Jenkins.class, "getActiveInstance");

        AbstractProject upstreamProject = mock(AbstractProject.class);

        AbstractBuild previousBuildUpstreamBuild = mock(AbstractBuild.class);
        when(previousBuildUpstreamBuild.toString()).thenReturn("previousBuildUpstreamBuild");

        AbstractBuild upstreamBuildBetweenPreviousAndCurrent = mock(AbstractBuild.class);
        when(upstreamBuildBetweenPreviousAndCurrent.toString()).thenReturn("upstreamBuildBetweenPreviousAndCurrent");

        AbstractBuild upstreamBuild = mock(AbstractBuild.class);
        when(upstreamBuild.toString()).thenReturn("upstreamBuild");

        createPreviousNextRelationShip(previousBuildUpstreamBuild, upstreamBuildBetweenPreviousAndCurrent,
                upstreamBuild);



        User user1 = mock(User.class);
        when(user1.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.should.not.be.included@example.com"));
        Set<User> badGuys1 = Sets.newHashSet(user1);
        when(previousBuildUpstreamBuild.getCulprits()).thenReturn(badGuys1);

        User user2 = mock(User.class);
        when(user2.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.must.be.included@example.com"));
        Set<User> badGuys2 = Sets.newHashSet(user2);
        when(upstreamBuildBetweenPreviousAndCurrent.getCulprits()).thenReturn(badGuys2);

        User user3 = mock(User.class);
        when(user3.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("this.one.must.be.included.too@example.com"));
        Set<User> badGuys3 = Sets.newHashSet(user3);
        when(upstreamBuild.getCulprits()).thenReturn(badGuys3);


        AbstractBuild previousBuild = mock(AbstractBuild.class);
        when(previousBuild.getResult()).thenReturn(Result.SUCCESS);
        when(previousBuild.getUpstreamRelationshipBuild(upstreamProject)).thenReturn(previousBuildUpstreamBuild);
        when(previousBuild.toString()).thenReturn("previousBuild");

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.FAILURE);
        when(build.getUpstreamRelationshipBuild(upstreamProject)).thenReturn(upstreamBuild);
        when(build.toString()).thenReturn("build");

        createPreviousNextRelationShip(previousBuild, build);

        BuildListener listener = mock(BuildListener.class);
        when(listener.getLogger()).thenReturn(System.out);

        Collection<AbstractProject> upstreamProjects = Collections.singleton(upstreamProject);

        MailSender sender = new MailSender("", false, false, "UTF-8", upstreamProjects);
        String emailList = sender.getCulpritsOfEmailList(upstreamProject, build, listener);

        assertFalse(emailList.contains("this.one.should.not.be.included@example.com"));
        assertTrue(emailList.contains("this.one.must.be.included@example.com"));
        assertTrue(emailList.contains("this.one.must.be.included.too@example.com"));
    }
    
    /**
     * Creates a previous/next relationship between the builds in the given order.
     */
    private static void createPreviousNextRelationShip(AbstractBuild... builds) {
        int max = builds.length - 1;
        
        for (int i = 0; i < builds.length; i++) {
            if (i < max) {
                when(builds[i].getNextBuild()).thenReturn(builds[i+1]);
            }
        }
        
        for (int i = builds.length - 1; i >= 0; i--) {
            if (i >= 1) {
                when(builds[i].getPreviousBuild()).thenReturn(builds[i-1]);
            }
        }
    }

    @Issue("SECURITY-372")
    @Test public void forbiddenMail() throws Exception {
        final Jenkins jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(jenkins.isUseSecurity()).thenReturn(true);
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.doReturn(jenkins).when(Jenkins.class, "getActiveInstance");
        ACL acl = mock(ACL.class);
        User authorizedU = mock(User.class);
        when(authorizedU.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("authorized@mycorp"));
        Authentication authorized = mock(Authentication.class);
        when(authorizedU.impersonate()).thenReturn(authorized);
        when(acl.hasPermission(authorized, Item.READ)).thenReturn(true);
        User unauthorizedU = mock(User.class);
        when(unauthorizedU.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("unauthorized@mycorp"));
        Authentication unauthorized = mock(Authentication.class);
        when(unauthorizedU.impersonate()).thenReturn(unauthorized);
        when(acl.hasPermission(unauthorized, Item.READ)).thenReturn(false);
        User externalU = mock(User.class);
        when(externalU.getProperty(Mailer.UserProperty.class)).thenReturn(new Mailer.UserProperty("someone@nowhere.net"));
        when(externalU.impersonate()).thenThrow(new UsernameNotFoundException(""));
        AbstractBuild<?, ?> build = mock(AbstractBuild.class);
        when(build.getCulprits()).thenReturn(Sets.newLinkedHashSet(Arrays.asList(authorizedU, unauthorizedU, externalU)));
        when(build.getACL()).thenReturn(acl);
        when(build.getFullDisplayName()).thenReturn("prj #1");
        StringWriter sw = new StringWriter();
        TaskListener listener = new StreamTaskListener(sw);
        assertEquals("authorized@mycorp", new MailSender("", false, true).getUserEmailList(listener, build));
        listener.getLogger().flush();
        assertThat(sw.toString(), containsString(Messages.MailSender_user_without_read("unauthorized@mycorp", "prj #1")));
        assertThat(sw.toString(), containsString(Messages.MailSender_unknown_user("someone@nowhere.net")));
        MailSender.SEND_TO_USERS_WITHOUT_READ = true;
        try {
            sw = new StringWriter();
            listener = new StreamTaskListener(sw);
            assertEquals("authorized@mycorp,unauthorized@mycorp", new MailSender("", false, true).getUserEmailList(listener, build));
            listener.getLogger().flush();
            assertThat(sw.toString(), containsString(Messages.MailSender_warning_user_without_read("unauthorized@mycorp", "prj #1")));
            assertThat(sw.toString(), containsString(Messages.MailSender_unknown_user("someone@nowhere.net")));
            MailSender.SEND_TO_UNKNOWN_USERS = true;
            try {
                sw = new StringWriter();
                listener = new StreamTaskListener(sw);
                assertEquals("authorized@mycorp,unauthorized@mycorp,someone@nowhere.net", new MailSender("", false, true).getUserEmailList(listener, build));
                listener.getLogger().flush();
                assertThat(sw.toString(), containsString(Messages.MailSender_warning_user_without_read("unauthorized@mycorp", "prj #1")));
                assertThat(sw.toString(), containsString(Messages.MailSender_warning_unknown_user("someone@nowhere.net")));
            } finally {
                MailSender.SEND_TO_UNKNOWN_USERS = false;
            }
        } finally {
            MailSender.SEND_TO_USERS_WITHOUT_READ = false;
        }
    }

}
