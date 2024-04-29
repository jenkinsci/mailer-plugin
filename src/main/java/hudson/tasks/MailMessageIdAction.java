/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.thoughtworks.xstream.annotations.XStreamConverter;
import hudson.model.Action;
import hudson.model.Run;
import hudson.util.LRUStringConverter;

/**
 * Remembers the message ID of the e-mail that was sent for the build.
 *
 * <p>
 * This allows us to send further updates as replies.
 *
 * @author Kohsuke Kawaguchi
 */
public class MailMessageIdAction implements Action {

    static {
        Run.XSTREAM.processAnnotations(MailMessageIdAction.class);
    }

    /**
     * Message ID of the e-mail sent for the build.
     */
    @XStreamConverter(LRUStringConverter.class)
    public final String messageId;

    public MailMessageIdAction(String messageId) {
        this.messageId = messageId;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Message Id"; // but this is never supposed to be displayed
    }

    public String getUrlName() {
        return null; // no web binding
    }
}
