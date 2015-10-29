/*
 * The MIT License
 * 
 * Copyright (c) 2012-2013 Kohsuke Kawaguchi, Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.ExtensionList;
import hudson.model.Hudson;
import hudson.model.User;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Bug;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author Kohsuke Kawaguchi, ogondza
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {MailAddressResolver.class, Mailer.class, Mailer.DescriptorImpl.class})
@PowerMockIgnore({"javax.security.*", "javax.xml.*"})
public class MailAddressResolverTest {

    private User user;
    private Jenkins jenkins;
    private Mailer.DescriptorImpl descriptor;

    @Before
    public void setUp() throws Exception {

        jenkins = PowerMockito.mock(Hudson.class);

        user = PowerMockito.mock(User.class);
        when(user.getFullName()).thenReturn("Full name");
        when(user.getId()).thenReturn("user_id");

        PowerMockito.spy(Mailer.class);
        descriptor = PowerMockito.mock(Mailer.DescriptorImpl.class);
        PowerMockito.doReturn(descriptor).when(Mailer.class, "descriptor");
    }

    @Test
    public void nameAlreadyIsAnAddress() throws Exception {

        validateUserPropertyAddress("user@example.com", "user@example.com", "");
    }

    @Test
    public void nameContainsAddress() throws Exception {

        validateUserPropertyAddress("user@example.com", "User Name <user@example.com>", "");
    }

    @Bug(5164)
    @Test
    public void test5164() throws Exception {

        validateUserPropertyAddress("user@example.com", "DOMAIN\\user", "@example.com");
    }

    private void validateUserPropertyAddress(
            String address, String username, String suffix
    ) throws Exception {

        PowerMockito.when(descriptor.getDefaultSuffix()).thenReturn(suffix);

        PowerMockito.when(user.getFullName()).thenReturn(username);
        PowerMockito.when(user.getId()).thenReturn(username.replace('\\','_'));

        String a = new UserPropertyMock(user, null).getConfiguredAddress();
        assertEquals(address, a);
    }

    private static class UserPropertyMock extends Mailer.UserProperty {

        public UserPropertyMock(User user, String emailAddress) {
            super(emailAddress);
            this.user = user;
        }
    }

    @Test
    public void doNotResolveWhenUsingFastResolution() throws Exception {

        final MailAddressResolver resolver = mockResolver();

        configure(resolver);

        final String address = MailAddressResolver.resolveFast(user);

        verify(resolver, never()).findMailAddressFor(user);

        assertNull(address);
    }

    @Test
    public void doResolveWhenNotUsingFastResolution() throws Exception {

        final MailAddressResolver resolver = mockResolver();
        PowerMockito.when(resolver.findMailAddressFor(user)).thenReturn("a@b.c");

        configure(resolver);

        final String address = MailAddressResolver.resolve(user);

        verify(resolver, times(1)).findMailAddressFor(user);

        assertEquals("a@b.c", address);
    }

    @Test
    public void doResolveWhenUsingExplicitlUserEmail() {
        final String testEmail = "very_strange_email@test.case";
        
        when(user.getProperty(Mailer.UserProperty.class)).thenReturn(
            new Mailer.UserProperty(testEmail));
        
        final String address = MailAddressResolver.resolveFast(user);
        assertEquals(testEmail, address);
    }
    
    private MailAddressResolver mockResolver() {

        return PowerMockito.mock(MailAddressResolver.class);
    }

    private void configure(final MailAddressResolver... resolvers) throws Exception {

        PowerMockito.spy(MailAddressResolver.class);

        PowerMockito.doReturn(new MockExtensionList(jenkins, resolvers))
            .when(MailAddressResolver.class, "all")
        ;
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
