/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Mudiaga Obada
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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;

/**
 * Checks email addresses if they should be excluded from sent emails.
 * 
 * <p>
 * This is an extension point of Jenkins. Plugins that contribute a new
 * implementation of this class should put {@link Extension} on your
 * implementation class, like this:
 * 
 * <pre>
 * &#64;Extension
 * class MyMailAddressFilter extends {@link MailAddressFilter} {
 *     ...
 *     @Override
 *    public boolean isFiltered(AbstractBuild<?, ?> build, BuildListener listener, InternetAddress address) {
 *         ...
 *         return isFilteredAddress;
 *     }
 * }
 * </pre>
 * 
 * @author Mudiaga Obada
 * @since 1.5XX
 */
public abstract class MailAddressFilter implements ExtensionPoint {

    /**
     * Checks if a given email should be excluded from the recipients of an
     * email.
     * 
     * @param build
     * 
     * @return true if given InternetAddress is to be excluded from the
     *         recipients
     */
    public abstract boolean isFiltered(AbstractBuild<?, ?> build, BuildListener listener, InternetAddress address);

    /**
     * Returns a copy of the given set of recipients excluding addresses that our filtered out.
     * @param build
     * @param listener
     * @param recipients
     * @return
     */
    public static Set<InternetAddress> getFilteredRecipients(AbstractBuild<?, ?> build, BuildListener listener,
            Set<InternetAddress> recipients) {

        Set<InternetAddress> rcp = new LinkedHashSet<InternetAddress>();

        for (InternetAddress address : recipients) {
            if (!isFilteredRecipient(address, listener, build)) {
                rcp.add(address);
            }
        }

        return rcp;

    }

    /**
     * Check if email address is to be excluded from recipient list by checking
     * with each {@link MailAddressFilter} extension
     * 
     * @param listener
     * @param build
     * @return User address or null if resolution failed
     */
    private static boolean isFilteredRecipient(InternetAddress address, BuildListener listener, AbstractBuild<?, ?> build) {

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Checking for filtered email address for \"" + address + "\"");
        }

        for (MailAddressFilter filter : all()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Checking for filtered email address for \"" + address + "\" with " + filter.getClass().getName());
            }
            if (filter.isFiltered(build, listener, address)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Filtered out email recipient " + address);
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Returns all the registered {@link MailAddressFilter} descriptors
     */
    public static ExtensionList<MailAddressFilter> all() {
        return Jenkins.getInstance().getExtensionList(MailAddressFilter.class);
    }

    private static final Logger LOGGER = Logger.getLogger(MailAddressFilter.class.getName());

}
