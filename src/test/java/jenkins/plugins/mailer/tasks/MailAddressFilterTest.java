/*
 * The MIT License
 * 
 * Copyright (c) 2012-2013 Kohsuke Kawaguchi
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
package jenkins.plugins.mailer.tasks;

import hudson.ExtensionList;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import jenkins.model.Jenkins;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Mudiaga Obada
 */
public class MailAddressFilterTest {

    private Hudson jenkins;
    private AbstractBuild<?, ?> build;
    private BuildListener listener;

    @Before
    public void setUp() throws Exception {

        jenkins = Mockito.mock(Hudson.class);
        build = Mockito.mock(AbstractBuild.class);
        listener = Mockito.mock(BuildListener.class);

    }

    // Without any extension, filter should return identical set
    @Test
    public void testIdentity() throws Exception {

      try (MockedStatic<Jenkins> mocked = Mockito.mockStatic(Jenkins.class)) {
        mocked.when(Jenkins::get).thenReturn(jenkins);

        Set<InternetAddress> rcp = getRecipients();

        configure(Collections.<MailAddressFilter> emptyList());

        Set<InternetAddress> filtered = MailAddressFilter.filterRecipients(build, listener, rcp);

        Assert.assertEquals(rcp.size(), filtered.size());
        for (InternetAddress a : rcp) {
            Assert.assertTrue(filtered.contains(a));
        }
      }

    }

    // With an extension, MailAddressFilter must exclude what extension filters
    @Test
    public void testFilterExtension() throws Exception {

      try (MockedStatic<Jenkins> mocked = Mockito.mockStatic(Jenkins.class)) {
        mocked.when(Jenkins::get).thenReturn(jenkins);

        InternetAddress filteredAddress = new InternetAddress("systemUser@example.com");

        Set<InternetAddress> rcp = getRecipients();
        rcp.add(filteredAddress);

        MailAddressFilter filter = Mockito.mock(MailAddressFilter.class);
        Mockito.when(filter.shouldFilter(build, listener, filteredAddress)).thenReturn(true);

        configure(Arrays.asList(filter));

        Set<InternetAddress> filtered = MailAddressFilter.filterRecipients(build, listener, rcp);

        Assert.assertEquals(rcp.size() - 1, filtered.size());

        Assert.assertTrue(!filtered.contains(filteredAddress));
      }

    }

    private Set<InternetAddress> getRecipients() throws AddressException {

        InternetAddress addr[] = InternetAddress.parse("user1, user2@local, user2@example.com", false);

        return new HashSet<InternetAddress>(Arrays.asList(addr));

    }

    private void configure(List<MailAddressFilter> filters) throws Exception {

        Mockito.when(jenkins.getExtensionList(MailAddressFilter.class)).thenReturn(new MockExtensionList(jenkins, filters));
    }

    private static class MockExtensionList extends ExtensionList<MailAddressFilter> {

        private List<MailAddressFilter> extensions;

        public MockExtensionList(final Jenkins jenkins, List<MailAddressFilter> filters) {

            super(jenkins, MailAddressFilter.class);

            extensions = Collections.unmodifiableList(filters);
        }

        @Override
        public Iterator<MailAddressFilter> iterator() {

            return extensions.iterator();
        }
    }

}
