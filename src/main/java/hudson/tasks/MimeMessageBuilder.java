/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Daniel Dyer, Jean-Baptiste Quenot
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

import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MimeMessageBuilder {

    public static final String X_JENKINS_JOB = "X-Jenkins-Job";
    public static final String X_JENKINS_RESULT = "X-Jenkins-Result";

    private String charset = "UTF-8";
    private String mimeType = "text/plain";
    private TaskListener listener;
    private String defaultSuffix;
    private String from;
    private String replyTo;
    private String recipients;
    private Run<?, ?> run;

    public MimeMessageBuilder() {
        if (Jenkins.getInstance() != null) {
            defaultSuffix = Mailer.descriptor().getDefaultSuffix();
            from = JenkinsLocationConfiguration.get().getAdminAddress();
            replyTo = Mailer.descriptor().getReplyToAddress();
        }
    }

    public MimeMessageBuilder setCharset(String charset) {
        assert charset != null;
        this.charset = charset;
        return this;
    }

    public MimeMessageBuilder setMimeType(String mimeType) {
        assert mimeType != null;
        this.mimeType = mimeType;
        return this;
    }

    public MimeMessageBuilder setListener(TaskListener listener) {
        assert listener != null;
        this.listener = listener;
        return this;
    }

    public MimeMessageBuilder setRun(Run<?, ?> run) {
        assert run != null;
        this.run = run;
        return this;
    }

    public MimeMessageBuilder setDefaultSuffix(String defaultSuffix) {
        assert defaultSuffix != null;
        this.defaultSuffix = defaultSuffix;
        return this;
    }

    public MimeMessageBuilder setFrom(String from) {
        assert from != null;
        this.from = from;
        return this;
    }

    public MimeMessageBuilder setReplyTo(String replyTo) {
        assert replyTo != null;
        this.replyTo = replyTo;
        return this;
    }

    public MimeMessageBuilder setRecipients(String recipients) {
        assert recipients != null;
        this.recipients = recipients;
        return this;
    }

    /**
     * Build a {@link MimeMessage} instance from the set of supplied parameters.
     * @return The {@link MimeMessage} instance;
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public MimeMessage buildMimeMessage() throws MessagingException, UnsupportedEncodingException {
        MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());

        msg.setContent("", mimeType);
        if (StringUtils.isNotBlank(from)) {
            msg.setFrom(toNormalizedAddress(from));
        }
        msg.setSentDate(new Date());

        addRunHeaders(msg);
        addRecipients(msg);

        if (StringUtils.isNotBlank(replyTo)) {
            msg.setReplyTo(new Address[]{toNormalizedAddress(replyTo)});
        }
        return msg;
    }

    public InternetAddress toNormalizedAddress(String address) throws UnsupportedEncodingException {
        if (address == null) {
            return null;
        }

        // if not a valid address (i.e. no '@'), then try adding suffix
        if (!address.contains("@")) {
            if (defaultSuffix != null && defaultSuffix.contains("@")) {
                address += defaultSuffix;
            } else {
                return null;
            }
        }

        try {
            return Mailer.StringToAddress(address, charset);
        } catch (AddressException e) {
            // report bad address, but try to send to other addresses
            if (listener != null) {
                listener.getLogger().println("Unable to send to address: " + address);
                e.printStackTrace(listener.error(e.getMessage()));
            }
            return null;
        }
    }

    public static void setInReplyTo(MimeMessage msg, String inReplyTo) throws MessagingException {
        if (msg == null || inReplyTo == null) {
            return;
        }
        msg.setHeader("In-Reply-To", inReplyTo);
        msg.setHeader("References", inReplyTo);
    }

    private void addRunHeaders(MimeMessage msg) throws MessagingException {
        if (run != null) {
            msg.addHeader(X_JENKINS_JOB, run.getParent().getFullName());
            msg.addHeader(X_JENKINS_RESULT, run.getResult().toString());
        }
    }

    private void addRecipients(MimeMessage msg) throws UnsupportedEncodingException, MessagingException {
        if (recipients != null) {
            Set<InternetAddress> rcp = new LinkedHashSet<InternetAddress>();
            StringTokenizer tokens = new StringTokenizer(recipients);

            while (tokens.hasMoreTokens()) {
                String address = tokens.nextToken();
                rcp.add(toNormalizedAddress(address));
            }

            if (!rcp.isEmpty()) {
                msg.setRecipients(Message.RecipientType.TO, rcp.toArray(new InternetAddress[rcp.size()]));
            }
        }
    }
}
