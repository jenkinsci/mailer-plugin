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
package jenkins.plugins.mailer.tasks;

import hudson.model.TaskListener;
import hudson.tasks.Mailer;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.HeaderTokenizer;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;

import java.io.UnsupportedEncodingException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builder for {@link MimeMessage}. This class is NOT thread-safe.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MimeMessageBuilder {

    private static final Logger LOGGER = Logger.getLogger(MimeMessageBuilder.class.getName());
    private static final String PARSING_ADDRESS_ERROR_MESSAGE = "Unable to parse Reply-To Addresses ";

    private String charset = "UTF-8";
    private String mimeType = "text/plain";
    private TaskListener listener;
    private String defaultSuffix;
    private String from;
    private Set<InternetAddress> replyTo = new LinkedHashSet<InternetAddress>();
    private String subject;
    private String body;
    private AddressFilter recipientFilter;
    private Set<InternetAddress> to = new LinkedHashSet<InternetAddress>();
    private Set<InternetAddress> cc = new LinkedHashSet<InternetAddress>();
    private Set<InternetAddress> bcc = new LinkedHashSet<InternetAddress>();

    public MimeMessageBuilder() {
        JenkinsLocationConfiguration jlc = JenkinsLocationConfiguration.get();
        if (jlc != null) {
            defaultSuffix = Mailer.descriptor().getDefaultSuffix();
            from = jlc.getAdminAddress();
            final String rto = Mailer.descriptor().getReplyToAddress();
            try {
                replyTo.addAll(toNormalizedAddresses(rto));
            } catch(UnsupportedEncodingException e) {
                logError(PARSING_ADDRESS_ERROR_MESSAGE + rto, e);
            }
        }
    }

    public MimeMessageBuilder setCharset(@NonNull String charset) {
        this.charset = charset;
        return this;
    }

    public MimeMessageBuilder setMimeType(@NonNull String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public MimeMessageBuilder setListener(TaskListener listener) {
        this.listener = listener;
        return this;
    }

    public MimeMessageBuilder setDefaultSuffix(@NonNull String defaultSuffix) {
        this.defaultSuffix = defaultSuffix;
        return this;
    }

    public MimeMessageBuilder setFrom(@NonNull String from) {
        this.from = from;
        return this;
    }

    public MimeMessageBuilder setReplyTo(@NonNull String replyTo) {
        try {
            final List<InternetAddress> addresses = toNormalizedAddresses(replyTo);
            // Done after to leave the current value untouched if there is a parsing error.
            this.replyTo.clear();
            this.replyTo.addAll(addresses);
        } catch(UnsupportedEncodingException e) {
            logError(PARSING_ADDRESS_ERROR_MESSAGE + replyTo, e);
        }
        return this;
    }

    public MimeMessageBuilder addReplyTo(@NonNull String replyTo) {
        try {
            this.replyTo.addAll(toNormalizedAddresses(replyTo));
        } catch(UnsupportedEncodingException e) {
            logError(PARSING_ADDRESS_ERROR_MESSAGE + replyTo, e);
        }
        return this;
    }


    public MimeMessageBuilder setSubject(@NonNull String subject) {
        this.subject = subject;
        return this;
    }

    public MimeMessageBuilder setBody(@NonNull String body) {
        this.body = body;
        return this;
    }

    public MimeMessageBuilder setRecipientFilter(AddressFilter recipientFilter) {
        this.recipientFilter = recipientFilter;
        return this;
    }

    public MimeMessageBuilder addRecipients(@NonNull String recipients) throws UnsupportedEncodingException {
        addRecipients(recipients, Message.RecipientType.TO);
        return this;
    }

    /**
     * Adds the given recipients to the current MIME message.
     * @param recipients one or more recipients
     * @param recipientType recipient type
     * @return the constructed message with the given recipients
     * @throws UnsupportedEncodingException in case of encoding problems
     */
    public MimeMessageBuilder addRecipients(@NonNull String recipients, @NonNull Message.RecipientType recipientType) throws UnsupportedEncodingException {
        StringTokenizer tokens = new StringTokenizer(recipients, " \t\n\r\f,");
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
     * @throws MessagingException in case the mail cannot be created
     * @throws UnsupportedEncodingException in case of encoding problems
     */
    public MimeMessage buildMimeMessage() throws MessagingException, UnsupportedEncodingException {
        MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());

        setJenkinsInstanceIdent(msg);

        msg.setContent("", contentType());
        if (from != null && !from.isBlank()) {
            msg.setFrom(toNormalizedAddress(from));
        }
        msg.setSentDate(new Date());

        addSubject(msg);
        addBody(msg);
        addRecipients(msg);

        if (!replyTo.isEmpty()) {
            msg.setReplyTo(toAddressArray(replyTo));
        }
        return msg;
    }

    private void setJenkinsInstanceIdent(MimeMessage msg) throws MessagingException {
        if (Jenkins.getInstanceOrNull() != null) {
            String encodedIdentity;
            try {
                RSAPublicKey publicKey = InstanceIdentity.get().getPublic();
                encodedIdentity = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            } catch (Throwable t) {
                // Ignore. Just don't add the identity header.
                 logError("Failed to set Jenkins Identity header on email.", t);
                return;
            }
            msg.setHeader("X-Instance-Identity", encodedIdentity);
        }
    }

    private static Address[] toAddressArray(Collection<InternetAddress> c) {
        if (c == null || c.isEmpty()) {
            return new Address[0];
        }
        final Address[] addresses = new Address[c.size()];
        c.toArray(addresses);
        return addresses;
    }

    public static void setInReplyTo(@NonNull MimeMessage msg, @NonNull String inReplyTo) throws MessagingException {
        msg.setHeader("In-Reply-To", inReplyTo);
        msg.setHeader("References", inReplyTo);
    }

    public static interface AddressFilter {
        Set<InternetAddress> apply(Set<InternetAddress> recipients);
    }

    private void addSubject(MimeMessage msg) throws MessagingException {
        if (subject != null) {
            msg.setSubject(subject);
        }
    }

    private void addBody(MimeMessage msg) throws MessagingException {
        if (body != null) {
            Multipart multipart = new MimeMultipart();
            BodyPart bodyPart = new MimeBodyPart();

            bodyPart.setContent(body, contentType());
            multipart.addBodyPart(bodyPart);
            msg.setContent(multipart);
        }
    }

    private String contentType() {
        return String.format("%s; charset=%s", mimeType, MimeUtility.quote(charset, HeaderTokenizer.MIME));
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
        final Collection<InternetAddress> recipients = recipientFilter != null ? recipientFilter.apply(recipientList) : recipientList;
        msg.setRecipients(recipientType, toAddressArray(recipients));
    }

    private List<InternetAddress> toNormalizedAddresses(String addresses) throws UnsupportedEncodingException {
        final List<InternetAddress> list = new LinkedList<>();
        if (addresses != null && !addresses.isBlank()) {
            StringTokenizer tokens = new StringTokenizer(addresses, " \t\n\r\f,");
            while (tokens.hasMoreTokens()) {
                String addressToken = tokens.nextToken();
                InternetAddress internetAddress = toNormalizedAddress(addressToken);
                if (internetAddress != null) {
                    list.add(internetAddress);
                }
            }
        }
        return list;
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
            return Mailer.stringToAddress(address, charset);
        } catch (AddressException e) {
            // report bad address, but try to send to other addresses
            logError("Unable to send to address: " + address, e);
            return null;
        }
    }

    private void logError(String message, Throwable t) {
        if (listener != null) {
            t.printStackTrace(listener.error(message));
        } else {
            LOGGER.log(Level.WARNING, message, t);
        }
    }
}
