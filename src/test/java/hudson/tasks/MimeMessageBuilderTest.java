/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
import hudson.model.Run;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MimeMessageBuilderTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setup() {
        JenkinsLocationConfiguration.get().setAdminAddress("tom.aaaa@gmail.com");
        Mailer.descriptor().setReplyToAddress("tom.aaaa@gmail.com");
    }

    @Test
    public void test_basic() throws UnsupportedEncodingException, MessagingException {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();

        messageBuilder.addRecipients("tom.xxxx@gmail.com, tom.yyyy@gmail.com");
        MimeMessage mimeMessage = messageBuilder.buildMimeMessage();

        // check from and reply-to
        Address[] from = mimeMessage.getFrom();
        Assert.assertNotNull(from);
        Assert.assertEquals(1, from.length);
        Assert.assertEquals("tom.aaaa@gmail.com", from[0].toString());
        Address[] replyTo = mimeMessage.getReplyTo();
        Assert.assertNotNull(from);
        Assert.assertEquals(1, replyTo.length);
        Assert.assertEquals("tom.aaaa@gmail.com", replyTo[0].toString());

        // check the recipient list...
        Address[] allRecipients = mimeMessage.getAllRecipients();
        Assert.assertNotNull(allRecipients);
        Assert.assertEquals(2, allRecipients.length);
        Assert.assertEquals("tom.xxxx@gmail.com", allRecipients[0].toString());
        Assert.assertEquals("tom.yyyy@gmail.com", allRecipients[1].toString());
    }
}
