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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import org.jvnet.hudson.test.Bug;
import org.mockito.MockedStatic;

/**
 * @author Kohsuke Kawaguchi, ogondza
 */
public class MailAddressResolverTest {

    private User user;
    private Jenkins jenkins;
    private Mailer.DescriptorImpl descriptor;

    @Before
    public void setUp() throws Exception {

        jenkins = mock(Hudson.class);

        user = mock(User.class);
        when(user.getFullName()).thenReturn("Full name");
        when(user.getId()).thenReturn("user_id");

        descriptor = mock(Mailer.DescriptorImpl.class);
    }

    @Test
    public void nameAlreadyIsAnAddress() throws Exception {

      try (MockedStatic<Mailer> mockedMailer = mockStatic(Mailer.class)) {
        mockedMailer.when(Mailer::descriptor).thenReturn(descriptor);
        validateUserPropertyAddress("user@example.com", "user@example.com", "");
      }
    }

    @Test
    public void nameContainsAddress() throws Exception {

      try (MockedStatic<Mailer> mockedMailer = mockStatic(Mailer.class)) {
        mockedMailer.when(Mailer::descriptor).thenReturn(descriptor);
        validateUserPropertyAddress("user@example.com", "User Name <user@example.com>", "");
      }
    }

    @Bug(5164)
    @Test
    public void test5164() throws Exception {

      try (MockedStatic<Mailer> mockedMailer = mockStatic(Mailer.class)) {
        mockedMailer.when(Mailer::descriptor).thenReturn(descriptor);
        validateUserPropertyAddress("user@example.com", "DOMAIN\\user", "@example.com");
      }
    }

    private void validateUserPropertyAddress(
            String address, String username, String suffix
    ) throws Exception {

        when(descriptor.getDefaultSuffix()).thenReturn(suffix);

        when(user.getFullName()).thenReturn(username);
        when(user.getId()).thenReturn(username.replace('\\','_'));

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
      try (MockedStatic<Mailer> mockedMailer = mockStatic(Mailer.class);
           MockedStatic<ExtensionList> mockedExtensionList = mockStatic(ExtensionList.class)) {
        mockedMailer.when(Mailer::descriptor).thenReturn(descriptor);

        final MailAddressResolver resolver = mockResolver();

        configure(mockedExtensionList, resolver);

        final String address = MailAddressResolver.resolveFast(user);

        verify(resolver, never()).findMailAddressFor(user);

        assertNull(address);
      }
    }

    @Test
    public void doResolveWhenNotUsingFastResolution() throws Exception {

      try (MockedStatic<ExtensionList> mockedExtensionList = mockStatic(ExtensionList.class)) {
        final MailAddressResolver resolver = mockResolver();
        when(resolver.findMailAddressFor(user)).thenReturn("a@b.c");

        configure(mockedExtensionList, resolver);

        final String address = MailAddressResolver.resolve(user);

        verify(resolver, times(1)).findMailAddressFor(user);

        assertEquals("a@b.c", address);
      }
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

        return mock(MailAddressResolver.class);
    }

    private void configure(final MockedStatic<ExtensionList> mockedExtensionList, final MailAddressResolver... resolvers) {
        mockedExtensionList.when(() -> ExtensionList.lookup(MailAddressResolver.class)).thenReturn(new MockExtensionList(jenkins, resolvers));
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
