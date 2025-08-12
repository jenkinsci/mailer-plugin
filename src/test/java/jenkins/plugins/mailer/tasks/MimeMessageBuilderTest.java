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
package jenkins.plugins.mailer.tasks;

import hudson.tasks.Mailer;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.mock_javamail.Mailbox;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class MimeMessageBuilderTest {
    /** Address constant A. */
    private static final String A = "tom.aaaa@gmail.com";
    /** Address constant X. */
    private static final String X = "tom.xxxx@gmail.com";
    /** Address constant Y. */
    private static final String Y = "tom.yyyy@gmail.com";
    /** Address constant Z. */
    private static final String Z = "tom.zzzz@gmail.com";

    @BeforeEach
    void setup(JenkinsRule rule) {
        JenkinsLocationConfiguration.get().setAdminAddress(A);
        Mailer.descriptor().setReplyToAddress(A);
    }

    @Test
    void test_construction() throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();

        messageBuilder.addRecipients("tom.xxxx@gmail.com, tom.yyyy@gmail.com");
        MimeMessage mimeMessage = messageBuilder.buildMimeMessage();

        // check from and reply-to
        Address[] from = mimeMessage.getFrom();
        assertNotNull(from);
        assertEquals(1, from.length);
        assertEquals(A, from[0].toString());
        Address[] replyTo = mimeMessage.getReplyTo();
        assertNotNull(from);
        assertEquals(1, replyTo.length);
        assertEquals(A, replyTo[0].toString());

        // check the recipient list...
        Address[] allRecipients = mimeMessage.getAllRecipients();
        assertNotNull(allRecipients);
        assertEquals(2, allRecipients.length);
        assertEquals(X, allRecipients[0].toString());
        assertEquals(Y, allRecipients[1].toString());

        // Make sure we can regen the instance identifier public key
        String encodedIdent = mimeMessage.getHeader("X-Instance-Identity")[0];
        byte[] image = Base64.getDecoder().decode(encodedIdent);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(image));
        assertNotNull(publicKey);
    }

    @Test
    @Issue("JENKINS-26758")
    void test_charset_utf8() throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();
        messageBuilder.setMimeType("text/html");
        messageBuilder.setBody("Synthèse");

        MimeMessage message = messageBuilder.buildMimeMessage();
        message.saveChanges();

        StringWriter sw = new StringWriter();
        ((MimeMultipart) message.getContent()).getBodyPart(0).writeTo(new WriterOutputStream(sw));
        assertEquals("""
                        Content-Type: text/html; charset=UTF-8
                        Content-Transfer-Encoding: quoted-printable
                        
                        Synth=C3=A8se""",
                sw.toString().replaceAll("\r\n?", "\n"));
    }

    @Test
    @Issue("JENKINS-26758")
    void test_charset_iso_8859_1() throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();
        messageBuilder.setMimeType("text/html");
        messageBuilder.setCharset("ISO-8859-1");
        messageBuilder.setBody("Synthèse");

        MimeMessage message = messageBuilder.buildMimeMessage();
        message.saveChanges();

        StringWriter sw = new StringWriter();
        ((MimeMultipart) message.getContent()).getBodyPart(0).writeTo(new WriterOutputStream(sw));
        assertEquals("""
                        Content-Type: text/html; charset=ISO-8859-1
                        Content-Transfer-Encoding: quoted-printable
                        
                        Synth=E8se""",
                sw.toString().replaceAll("\r\n?", "\n"));
    }

    @Test
    void test_send() throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();

        messageBuilder
                .addRecipients("tom.xxxx@jenkins.com")
                .setSubject("Hello")
                .setBody("Testing email");

        MimeMessage mimeMessage = messageBuilder.buildMimeMessage();

        Mailbox.clearAll();
        Transport.send(mimeMessage);

        Mailbox mailbox = Mailbox.get("tom.xxxx@jenkins.com");
        assertEquals(1, mailbox.getNewMessageCount());
        Message message = mailbox.get(0);
        assertEquals("Hello", message.getSubject());
        assertEquals("Testing email", ((MimeMultipart) message.getContent()).getBodyPart(0).getContent().toString());
    }

    @Test
    @Issue("JENKINS-26606")
    void test_addRecipients_tokenizer() throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();

        messageBuilder.addRecipients("tom.xxxx@gmail.com,tom.yyyy@gmail.com tom.zzzz@gmail.com");
        MimeMessage mimeMessage = messageBuilder.buildMimeMessage();

        Address[] recipients = mimeMessage.getAllRecipients();
        assertEquals(3, recipients.length);
        assertEquals("tom.xxxx@gmail.com", recipients[0].toString());
        assertEquals("tom.yyyy@gmail.com", recipients[1].toString());
        assertEquals("tom.zzzz@gmail.com", recipients[2].toString());
    }

    @Test
    @Issue("JENKINS-32301")
    void testMultipleReplyToAddress() throws Exception {
        checkMultipleReplyToAddress("%s %s %s");
        checkMultipleReplyToAddress("%s  , %s %s");
        checkMultipleReplyToAddress("%s %s, %s");
        checkMultipleReplyToAddress("%s,%s,%s");
        checkMultipleReplyToAddress(new MimeMessageBuilder().setReplyTo(X).addReplyTo(Y).addReplyTo(Z));
    }

    private void checkMultipleReplyToAddress(String replyTo) throws Exception {
        checkMultipleReplyToAddress(new MimeMessageBuilder().setReplyTo(String.format(replyTo, X, Y, Z)));
    }

    private void checkMultipleReplyToAddress(MimeMessageBuilder messageBuilder) throws Exception {
        MimeMessage mimeMessage = messageBuilder.buildMimeMessage();
        Address[] recipients = mimeMessage.getReplyTo();
        assertEquals(3, recipients.length);
        assertEquals(X, recipients[0].toString());
        assertEquals(Y, recipients[1].toString());
        assertEquals(Z, recipients[2].toString());
    }
}
