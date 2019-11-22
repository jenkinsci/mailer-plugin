package hudson.tasks;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;

public class EnhancedMessage extends MimeMessage
{
    private String newMessageIdDomain;

    public EnhancedMessage(Session session) {
        super(session);
    }

    public EnhancedMessage(Session session, InputStream is) throws MessagingException {
        super(session, is);
    }

    public EnhancedMessage(MimeMessage source) throws MessagingException {
        super(source);
    }

    public void setNewMessageIdDomain(String newDomain)
    {
        this.newMessageIdDomain = newDomain;
    }

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
