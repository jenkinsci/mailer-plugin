package jenkins.plugins.mailer.tasks;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A special class for overriding Message-ID header.
 * Some e-mail servers put the mail into spam if Message-ID header domain
 * does not correspond to e-mail server domain. This class helps to overcome
 * the issue.
 *
 * @author Vitaly Krivenko
 */
@Restricted(NoExternalUse.class)
public class EnhancedMessage extends MimeMessage
{
    /**
     * The new Message-ID domain that will override the default one.
     */
    private String newMessageIdDomain;

    /**
     * Initializes EnhancedMessage class instance.
     * @param session the session context
     */
    public EnhancedMessage(Session session) {
        super(session);
    }

    /**
     * Initializes EnhancedMessage class instance.
     * @param session the session context
     * @param is the message input stream
     */
    public EnhancedMessage(Session session, InputStream is) throws MessagingException {
        super(session, is);
    }

    /**
     * Initializes EnhancedMessage class instance.
     * @param source the message to copy
     */
    public EnhancedMessage(EnhancedMessage source) throws MessagingException {
        super(source);
    }

    /**
     * Sets the new Message-ID header domain which will override the default one.
     * @param newDomain The new Message-ID header domain
     */
    public void setNewMessageIdDomain(String newDomain)
    {
        this.newMessageIdDomain = newDomain;
    }

    /**
     * Overrides default javamail behaviour by replacing the default Message-ID domain with the new one.
     */
    @Override
    protected void updateMessageID() throws MessagingException {
        super.updateMessageID();
        if (this.newMessageIdDomain != null && this.newMessageIdDomain.length() > 0 && this.newMessageIdDomain.startsWith("@")) {
            String messageId = super.getMessageID();
            int index = messageId.lastIndexOf('@');
            if (index >= 0) {
                String firstPart = messageId.substring(0, index);
                String newMessageId = firstPart + this.newMessageIdDomain + '>';
                setHeader("Message-ID", newMessageId);
            }
        }
    }
}
