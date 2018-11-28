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
package jenkins.plugins.mailer.tasks;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
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
 * implementation of this class should extend {@link Extension} on your
 * implementation class, like this:
 * 
 * <pre>
 * &#64;Extension
 * class MyMailAddressFilter extends {@link MailAddressFilter} {
 *    ...
 *     &#64;Extension
 *     public static class DescriptorImpl extends MailAddressFilterDescriptor {
 *         &#64;Override
 *         public String getDisplayName() {
 *             return "myMailAddressFilterExtension";
 *         }
 *     }
 * }
 * </pre>
 * 
 * @author Mudiaga Obada
 * @since 1.9
 */
public abstract class MailAddressFilter implements Describable<MailAddressFilter>, ExtensionPoint {

    /**
     * Checks if a given email should be excluded from the recipients of an
     * email.
     *
     * @param build the current build.
     * @param listener the task listener
     * @param address email address
     * @return {@code true} if given InternetAddress is to be excluded from the
     *         recipients
     */
    public boolean shouldFilter(Run<?,?> build, TaskListener listener, InternetAddress address) {
        if (Util.isOverridden(MailAddressFilter.class, getClass(), "isFiltered", AbstractBuild.class, BuildListener.class, InternetAddress.class) && build instanceof AbstractBuild && listener instanceof BuildListener) {
            return isFiltered((AbstractBuild) build, (BuildListener) listener, address);
        } else {
            throw new AbstractMethodError("you must implement shouldFilter");
        }
    }

    @Deprecated
    public boolean isFiltered(AbstractBuild<?, ?> build, BuildListener listener, InternetAddress address) {
        if (Util.isOverridden(MailAddressFilter.class, getClass(), "shouldFilter", Run.class, TaskListener.class, InternetAddress.class)) {
            return shouldFilter(build, listener, address);
        } else {
            throw new AbstractMethodError("you must implement shouldFilter");
        }
    }

    /**
     * Returns a copy of the given set of recipients excluding addresses that are filtered out.
     * @param build the current build.
     * @param listener the task listener
     * @param recipients set of recipients
     * @return filtered list of recipients
     */
    public static Set<InternetAddress> filterRecipients(Run<?,?> build, TaskListener listener, Set<InternetAddress> recipients) {

        Set<InternetAddress> rcp = new LinkedHashSet<InternetAddress>();

        for (InternetAddress address : recipients) {
            if (!isFilteredRecipient(address, listener, build)) {
                rcp.add(address);
            }
        }

        return rcp;

    }

    @Deprecated
    public static Set<InternetAddress> getFilteredRecipients(AbstractBuild<?, ?> build, BuildListener listener,
            Set<InternetAddress> recipients) {
        return filterRecipients(build, listener, recipients);
    }

    /**
     * Check if email address is to be excluded from recipient list by checking
     * with each {@link MailAddressFilter} extension
     * 
     * @param listener
     * @param build
     * @return User address or null if resolution failed
     */
    private static boolean isFilteredRecipient(InternetAddress address, TaskListener listener, Run<?, ?> build) {

        LOGGER.log(Level.FINE, "Checking for filtered email address for \"{0}\"", address);
        
        for (MailAddressFilter filter : allExtensions()) {
            LOGGER.log(Level.FINE, "Checking for filtered email address for \"{0}\" with \"{1}\"", 
                    new Object[] { address, filter.getClass().getName() });
            if (filter.shouldFilter(build, listener, address)) {
                LOGGER.log(Level.FINE, "Filtered out email recipient \"{0}\"", address);
                return true;
            }
        }

        return false;
    }

    @Override
    public MailAddressFilterDescriptor getDescriptor() {
        // TODO 1.590+ Jenkins.getActiveInstance
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            return (MailAddressFilterDescriptor)jenkins.getDescriptor(getClass());
        } else {
            throw new IllegalStateException("Jenkins is not active.");
        }
    }
    
    /**
     * @return all of the registered {@link MailAddressFilter} descriptors
     */
    public static DescriptorExtensionList<MailAddressFilter,MailAddressFilterDescriptor> all() {
        // TODO 1.590+ Jenkins.getActiveInstance
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            return jenkins.<MailAddressFilter, MailAddressFilterDescriptor>getDescriptorList(MailAddressFilter.class);
        } else {
            throw new IllegalStateException("Jenkins is not active.");
        }
    }
    
    public static ExtensionList<MailAddressFilter> allExtensions() {
        // TODO 1.590+ Jenkins.getActiveInstance
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            return jenkins.getExtensionList(MailAddressFilter.class);
        } else {
            throw new IllegalStateException("Jenkins is not active.");
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MailAddressFilter.class.getName());

}
