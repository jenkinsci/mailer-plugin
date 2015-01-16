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

import hudson.model.TaskListener;
import hudson.remoting.Base64;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MimeMessageBuilder {

    private String charset = "UTF-8";
    private String mimeType = "text/plain";
    private TaskListener listener;
    private String defaultSuffix;
    private String from;
    private String replyTo;
    private AddressFilter recipientFilter;
    private Set<InternetAddress> to = new LinkedHashSet<InternetAddress>();
    private Set<InternetAddress> cc = new LinkedHashSet<InternetAddress>();
    private Set<InternetAddress> bcc = new LinkedHashSet<InternetAddress>();

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
        this.listener = listener;
        return this;
    }

    public MimeMessageBuilder setDefaultSuffix(String defaultSuffix) {
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

    public MimeMessageBuilder setRecipientFilter(AddressFilter recipientFilter) {
        this.recipientFilter = recipientFilter;
        return this;
    }

    public MimeMessageBuilder addRecipients(String recipients) throws UnsupportedEncodingException {
        addRecipients(recipients, Message.RecipientType.TO);
        return this;
    }

    public MimeMessageBuilder addRecipients(String recipients, Message.RecipientType recipientType) throws UnsupportedEncodingException {
        assert recipients != null;
        assert recipientType != null;

        StringTokenizer tokens = new StringTokenizer(recipients);
        while (tokens.hasMoreTokens()) {
            String addressToken = tokens.nextToken();
            InternetAddress internetAddress = toNormalizedAddress(addressToken);

            if (internetAddress != null) {
                if (recipientType == Message.RecipientType.TO) {
                    to.add(internetAddress);
                } else if (recipientType == Message.RecipientType.CC) {
                    cc.add(internetAddress);
                } else if (recipientType == Message.RecipientType.BCC) {
                    bcc.add(internetAddress);
                }
            }
        }

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

        setJenkinsInstanceIdent(msg);

        msg.setContent("", mimeType);
        if (StringUtils.isNotBlank(from)) {
            msg.setFrom(toNormalizedAddress(from));
        }
        msg.setSentDate(new Date());

        addRecipients(msg);

        if (StringUtils.isNotBlank(replyTo)) {
            msg.setReplyTo(new Address[]{toNormalizedAddress(replyTo)});
        }
        return msg;
    }

    private void setJenkinsInstanceIdent(MimeMessage msg) throws MessagingException {
        if (Jenkins.getInstance() != null) {
            RSAPublicKey publicKey = InstanceIdentity.get().getPublic();
            msg.setHeader("X-Instance-Identity", Base64.encode(publicKey.getEncoded()));
        }
    }

    public static void setInReplyTo(MimeMessage msg, String inReplyTo) throws MessagingException {
        if (msg == null || inReplyTo == null) {
            return;
        }
        msg.setHeader("In-Reply-To", inReplyTo);
        msg.setHeader("References", inReplyTo);
    }

    public static interface AddressFilter {
        Set<InternetAddress> apply(Set<InternetAddress> recipients);
    }

    private void addRecipients(MimeMessage msg) throws UnsupportedEncodingException, MessagingException {
        addRecipients(msg, to, Message.RecipientType.TO);
        addRecipients(msg, cc, Message.RecipientType.CC);
        addRecipients(msg, bcc, Message.RecipientType.BCC);
    }
    private void addRecipients(MimeMessage msg, Set<InternetAddress> recipientList, Message.RecipientType recipientType) throws UnsupportedEncodingException, MessagingException {
        if (recipientList.isEmpty()) {
            return;
        }

        if (recipientFilter != null) {
            Set<InternetAddress> filteredList = recipientFilter.apply(recipientList);
            msg.setRecipients(recipientType, filteredList.toArray(new InternetAddress[filteredList.size()]));
        } else {
            msg.setRecipients(recipientType, recipientList.toArray(new InternetAddress[recipientList.size()]));
        }
    }

    private InternetAddress toNormalizedAddress(String address) throws UnsupportedEncodingException {
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
}
