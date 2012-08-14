package hudson.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.ExtensionList;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.tasks.Mailer.UserProperty;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author Kohsuke Kawaguchi
 */
public class MailAddressResolverTest extends HudsonTestCase {
    
    @Bug(5164)
    public void test5164() {
        Mailer.descriptor().setDefaultSuffix("@example.com");
        String a = User.get("DOMAIN\\user").getProperty(UserProperty.class).getAddress();
        assertEquals("user@example.com",a);
    }

    /**
     * @author ogondza
     */
    @RunWith(PowerMockRunner.class)
    @PrepareForTest( {MailAddressResolver.class, Mailer.class, Mailer.DescriptorImpl.class})
    public static class BehaviouralTest {
        
        private User user;
        private Jenkins jenkins;
        
        @Before
        public void setUp () {
            
            jenkins = Mockito.mock(Hudson.class);
            user = Mockito.mock(User.class);
            when(user.getFullName()).thenReturn("Full name");
            when(user.getId()).thenReturn("user_id");
        }
    
    

        @Test
        public void useAllResolversWhenNothingFound() throws Exception {
            
            final MailAddressResolver[] resolvers = {
                    Mockito.mock(MailAddressResolver.class),
                    Mockito.mock(MailAddressResolver.class)
            };
                            
            configure(resolvers, true);
                                   
            final String address = MailAddressResolver.resolve(user);
            
            verify(resolvers[0]).findMailAddressFor(user);
            verify(resolvers[1]).findMailAddressFor(user);
            
            assertNull(address);
        }
        
        @Test
        public void stopResolutionWhenAddressFound() throws Exception {
            
            final MailAddressResolver[] resolvers = {
                    Mockito.mock(MailAddressResolver.class),
                    Mockito.mock(MailAddressResolver.class)
            };
            
            when(resolvers[0].findMailAddressFor(user)).thenReturn("mail@addr.com");
                            
            configure(resolvers, true);
                                   
            final String address = MailAddressResolver.resolve(user);
            
            verify(resolvers[0]).findMailAddressFor(user);
            verify(resolvers[1], never()).findMailAddressFor(user);
            
            assertEquals(address, "mail@addr.com");
        }
        
        @Test
        public void doNetResolveWhenForbidden() throws Exception {
            
            final MailAddressResolver[] resolvers = {
                    Mockito.mock(MailAddressResolver.class)
            };
            
            configure(resolvers, false);
                                   
            final String address = MailAddressResolver.resolve(user);
            
            verify(resolvers[0], never()).findMailAddressFor(user);
            
            assertNull(address);
        }
        
        private void configure(
                final MailAddressResolver[] resolvers,
                final boolean resolve
        ) throws Exception {
            
            PowerMockito.spy(Mailer.class);
            PowerMockito.doReturn(getResolveDescriptor(resolve))
                    .when(Mailer.class, "descriptor")
            ;
            
            PowerMockito.spy(MailAddressResolver.class);
            
            PowerMockito.doReturn(getResolverListMock(jenkins, resolvers))
                .when(MailAddressResolver.class, "all")
            ;
        }
        
        private Mailer.DescriptorImpl getResolveDescriptor(final boolean answer) {
            
            final Mailer.DescriptorImpl descriptor = PowerMockito.mock(Mailer.DescriptorImpl.class);
            
            when(descriptor.getTryToResolve()).thenReturn(answer);
            
            return descriptor;
        }
        
        private ExtensionList<MailAddressResolver> getResolverListMock(
                final Jenkins jenkins,
                final MailAddressResolver[] resolvers
        ) {
            
            return new MockExtensionList(jenkins, resolvers);
        }
        
        private static class MockExtensionList extends ExtensionList<MailAddressResolver> {
            
            private List<MailAddressResolver> extensions;
            
            public MockExtensionList(
                    final Jenkins jenkins,
                    final MailAddressResolver[] resolvers
            ) {
                
                super(jenkins, MailAddressResolver.class);
                
                extensions = Arrays.asList(resolvers);
            }
            
            @Override
            public Iterator<MailAddressResolver> iterator() {
                
                return extensions.iterator();
            }
        }
    }
}
