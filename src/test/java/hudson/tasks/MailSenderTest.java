package hudson.tasks;

import com.google.common.collect.Sets;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.acegisecurity.Authentication;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 * Test case for the {@link MailSender}
 * 
 * See also {@link MailerTest} for more tests for the mailer.
 * 
 * @author Christoph Kutzinski
 */
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
        AbstractProject upstreamProject = mock(AbstractProject.class);

        ACL acl = mock(ACL.class);
        when(acl.hasPermission(any(Authentication.class), eq(Item.READ))).thenReturn(true);

        AbstractBuild previousBuildUpstreamBuild = mock(AbstractBuild.class);
        when(previousBuildUpstreamBuild.toString()).thenReturn("previousBuildUpstreamBuild");

        AbstractBuild upstreamBuildBetweenPreviousAndCurrent = mock(AbstractBuild.class);
        when(upstreamBuildBetweenPreviousAndCurrent.toString()).thenReturn("upstreamBuildBetweenPreviousAndCurrent");
        when(upstreamBuildBetweenPreviousAndCurrent.getACL()).thenReturn(acl);

        AbstractBuild upstreamBuild = mock(AbstractBuild.class);
        when(upstreamBuild.toString()).thenReturn("upstreamBuild");
        when(upstreamBuild.getACL()).thenReturn(acl);

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

}
